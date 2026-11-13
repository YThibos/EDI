import { Location } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideLocationMocks } from '@angular/common/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { routes } from '../app.routes';
import { EVENT_SOURCES } from '../console/fleet-stream';
import { sitrepFixture } from '../console/sitrep.fixture';
import { AnsweredRounds, TaskDetail } from '../console/task-detail';

const LAMP: TaskDetail = {
  task: {
    id: 'fit-lamp',
    intake: { kind: 'ship', title: 'Fit the lamp', projects: ['harness'], workKey: 'EDI-31', base: 'main', blockedBy: [], delivery: null },
    stage: 'working',
    notes: '',
    hold: null,
    created: '2026-10-15T06:00:00Z',
  },
  waits: [{ task: 'build-tower', awaited: 'landed', stage: 'merged', met: false }, { task: 'pave-quay', awaited: 'merged', stage: 'retired', met: true }],
  decision: {
    number: 'D58',
    card: {
      title: 'Which lamp?',
      context: 'Two fit the tower.',
      options: [{ key: 'A', text: 'LED' }, { key: 'B', text: 'Oil' }],
      recommendation: { key: 'A', why: 'it lasts' },
    },
    raisedAt: '2026-10-15T06:10:00Z',
    answer: { verbatim: 'D58 A', option: 'A', answeredAt: '2026-10-15T07:00:00Z', channel: 'console' },
    open: false,
  },
  delivery: { address: { number: 12 }, draft: true, state: 'open' },
  worked: { active: 'PT1H5M', wallClock: 'PT3H' },
  cost: null,
  timeline: [
    { sequence: 1, at: '2026-10-15T06:00:00Z', actor: 'primary', type: 'TaskAdded' },
    { sequence: 2, at: '2026-10-15T06:10:00Z', actor: 'worker:fit-lamp#1', type: 'DecisionRaised' },
  ],
};

const ROUNDS: AnsweredRounds = {
  task: 'fit-lamp',
  rounds: [{ number: 1, answeredAt: '2026-10-15T08:00:00Z', answers: [{ decision: 'D58', verbatim: 'the LED one', option: 'A', refusedBecause: null }] }],
};

class SilentEventSource {
  onopen: (() => void) | null = null;
  onerror: (() => void) | null = null;

  addEventListener(): void {
    return;
  }

  close(): void {
    return;
  }
}

describe('TaskPanel', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter(routes),
        provideLocationMocks(),
        { provide: EVENT_SOURCES, useValue: () => new SilentEventSource() },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  it('opens a task by its address over the sitrep, with its facts and chips, beside the decisions column', async () => {
    const page = await opened('/task/fit-lamp');

    const panel = page.querySelector('app-task-panel') as HTMLElement;
    expect(panel.getAttribute('role')).toBe('dialog');
    expect(panel.querySelector('.sr-task__title')?.textContent).toBe('Fit the lamp');
    expect(facts(panel)).toEqual({
      Kind: 'ship',
      Stage: 'working',
      Projects: 'harness',
      'Work key': 'EDI-31',
      Base: 'main',
      'Waits for': 'build-tower to land (merged)',
      Request: '#12 · open · draft',
      Worked: 'active 1h05m of 3h00m',
      Added: '2026-10-15 08:00 CEST',
    });
    expect(panel.parentElement?.classList).toContain('sr-cols');
    expect(page.querySelector('.sr-sign--amber')?.textContent).toContain('Needs your decision');
    expect(document.title).toBe('(1) SITREP · EDI');
  });

  it('says nothing waits once every wait of the task is met', async () => {
    const page = await opened('/task/fit-lamp', { ...LAMP, waits: [{ task: 'build-tower', awaited: 'merged', stage: 'merged', met: true }] });

    const panel = page.querySelector('app-task-panel') as HTMLElement;
    expect(Object.keys(facts(panel))).not.toContain('Waits for');
  });

  it('shows the timeline first and the decision with its answer on its tab', async () => {
    const page = await opened('/task/fit-lamp');
    const panel = page.querySelector('app-task-panel') as HTMLElement;
    const timeline = texts(panel, '.sr-task__timeline li');

    tab(panel, 'Decisions').click();
    await settled();

    expect(timeline).toEqual(['2026-10-15 08:00 CEST task added · primary', '2026-10-15 08:10 CEST decision raised · worker:fit-lamp#1']);
    expect(panel.querySelector('.sr-task__decision h3')?.textContent).toBe('D58 Which lamp?');
    expect(texts(panel, '.sr-task__chosen')).toEqual(['A LED']);
    expect(panel.textContent).toContain('Answered "D58 A" in the console, 2026-10-15 09:00 CEST');
  });

  it('renders the brief and the report inside the panel and says when one does not exist yet', async () => {
    const page = await opened('/task/fit-lamp');
    const panel = page.querySelector('app-task-panel') as HTMLElement;

    tab(panel, 'Brief').click();
    await settled();
    http.expectOne('/api/tasks/fit-lamp/pages/brief').flush('<!doctype html><html><head><title>fit-lamp brief · EDI</title></head><body><h1>Fit the lamp</h1><p>Use the <em>LED</em>.</p></body></html>');
    await settled();
    const brief = panel.querySelector('.sr-task__page')?.innerHTML;
    tab(panel, 'Report').click();
    await settled();
    http.expectOne('/api/tasks/fit-lamp/pages/report').flush('fit-lamp has no report yet.\n', { status: 404, statusText: 'Not Found' });
    await settled();

    expect(brief).toBe('<h1>Fit the lamp</h1><p>Use the <em>LED</em>.</p>');
    expect(panel.querySelector('.sr-task__page')).toBeNull();
    expect(panel.querySelector('.sr-task__body .sr-empty')?.textContent).toBe('fit-lamp has no report yet.');
  });

  it('lists the answered board rounds from the read-only board endpoint', async () => {
    const page = await opened('/task/fit-lamp');
    const panel = page.querySelector('app-task-panel') as HTMLElement;

    tab(panel, 'Board rounds').click();
    await settled();
    http.expectOne('/api/tasks/fit-lamp/board').flush(ROUNDS);
    await settled();

    expect(panel.querySelector('.sr-task__round h3')?.textContent).toBe('Round 1 · answered 2026-10-15 10:00 CEST');
    expect(texts(panel, '.sr-task__round li')).toEqual(['D58 A "the LED one"']);
  });

  it('returns to the sitrep on Esc and on its close control', async () => {
    const page = await opened('/task/fit-lamp');
    const location = TestBed.inject(Location);

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    await settled();
    const afterEsc = location.path();
    await reopened('/task/fit-lamp');
    (page.querySelector('.sr-task__close') as HTMLButtonElement).click();
    await settled();

    expect(afterEsc).toBe('/');
    expect(location.path()).toBe('/');
    expect(page.querySelector('app-task-panel')).toBeNull();
  });

  it('opens a row\'s panel from its title, at an address that survives a reload', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/');
    http.expectOne('/api/sitrep').flush(sitrepFixture());
    await harness.fixture.whenStable();
    const page = harness.fixture.nativeElement as HTMLElement;

    const title = Array.from(page.querySelectorAll<HTMLAnchorElement>('.sr-row__open')).find((link) => link.textContent === 'Fit the lamp');
    title?.click();
    await harness.fixture.whenStable();
    http.expectOne('/api/tasks/fit-lamp').flush(LAMP);
    await harness.fixture.whenStable();

    expect(title?.getAttribute('href')).toBe('/task/fit-lamp');
    expect(TestBed.inject(Location).path()).toBe('/task/fit-lamp');
    expect(page.querySelector('.sr-task__title')?.textContent).toBe('Fit the lamp');
  });

  it('has no control that changes the fleet: every request it makes only reads', async () => {
    const page = await opened('/task/fit-lamp');
    const panel = page.querySelector('app-task-panel') as HTMLElement;

    for (const name of ['Decisions', 'Brief', 'Report', 'Board rounds', 'Timeline']) {
      tab(panel, name).click();
      await settled();
    }
    const requests = http.match(() => true);
    requests.forEach((request) => request.flush(''));

    expect(requests.map((request) => request.request.method)).toEqual(['GET', 'GET', 'GET']);
    expect(panel.querySelectorAll('input, textarea, select, form')).toHaveLength(0);
    expect(texts(panel, 'button')).toEqual(['Close', 'Timeline', 'Live', 'Decisions', 'Brief', 'Report', 'Board rounds']);
  });

  let harness: RouterTestingHarness;

  async function opened(address: string, detail: TaskDetail = LAMP): Promise<HTMLElement> {
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(address);
    http.expectOne('/api/sitrep').flush(sitrepFixture());
    await harness.fixture.whenStable();
    http.expectOne('/api/tasks/fit-lamp').flush(detail);
    await settled();
    return harness.fixture.nativeElement as HTMLElement;
  }

  async function reopened(address: string): Promise<void> {
    await harness.navigateByUrl(address);
    http.expectOne('/api/tasks/fit-lamp').flush(LAMP);
    await settled();
  }

  async function settled(): Promise<void> {
    harness.detectChanges();
    await harness.fixture.whenStable();
  }
});

function facts(panel: HTMLElement): Record<string, string> {
  return Object.fromEntries(Array.from(panel.querySelectorAll('.sr-task__facts > div')).map((fact) =>
    [fact.querySelector('dt')?.textContent?.trim() ?? '', fact.querySelector('dd')?.textContent?.trim() ?? '']));
}

function tab(panel: HTMLElement, name: string): HTMLButtonElement {
  return Array.from(panel.querySelectorAll<HTMLButtonElement>('.sr-task__tab')).find((button) => button.textContent?.trim() === name) as HTMLButtonElement;
}

function texts(root: HTMLElement, selector: string): string[] {
  return Array.from(root.querySelectorAll(selector)).map((element) => element.textContent?.replace(/\s+/g, ' ').trim() ?? '');
}
