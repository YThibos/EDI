import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { EVENT_SOURCES } from '../console/fleet-stream';
import { personalityFixture } from '../console/personality.fixture';
import { sitrepFixture } from '../console/sitrep.fixture';
import { SitrepPage } from './sitrep-page';

class FakeEventSource {
  onopen: (() => void) | null = null;
  onerror: (() => void) | null = null;
  readonly listeners = new Map<string, () => void>();
  closed = false;

  addEventListener(name: string, listener: () => void): void {
    this.listeners.set(name, listener);
  }

  close(): void {
    this.closed = true;
  }
}

const NO_STEPS = { steps: [], landingOrder: null };

describe('SitrepPage', () => {
  let http: HttpTestingController;
  let source: FakeEventSource;

  beforeEach(() => {
    source = new FakeEventSource();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), { provide: EVENT_SOURCES, useValue: () => source }],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it('shows the fleet in the approved board sections', async () => {
    const page = (await rendered()).nativeElement as HTMLElement;

    expect(texts(page, '.sr-stat__num')).toEqual(['1', '1', '1', '23']);
    expect(page.querySelector('.sr-stat')?.classList).toContain('sr-stat--hot');
    expect(page.querySelector('.sr-clock__sd')?.textContent).toBe('SD 2026.288 · 08:17 CEST');
    expect(page.querySelector('.sr-brand__wm')?.textContent).toBe('EDI');
    expect(text(page, '.sr-needs .sr-sub')).toBe('1 waiting · 1 dealt with');
    expect(texts(page, '.sr-entry__title')).toEqual(['D58 Which lamp?', 'D57 Which horn?']);
    expect(texts(page, '.sr-row__title')).toEqual(['EDI-31 Fit the lamp', 'Tune the horn', 'Scrub the deck']);
    expect(page.textContent).toContain('showing 2 of 23');
    expect(page.textContent).toContain('+21 more queued');
  });

  it('shows every time on the page in the machine’s zone that the core names', async () => {
    const page = (await rendered(sitrepFixture({ zone: 'Europe/London' }))).nativeElement as HTMLElement;

    expect(page.querySelector('.sr-clock__sd')?.textContent).toBe('SD 2026.288 · 07:17 BST');
    expect(text(page, '.sr-entry--dealt .sr-entry__sub')).toBe('answered option B, in the console at 06:40 BST · horn-choice');
    expect(text(page, '.sr-foot .sr-meta-mono')).toBe('edi-sitrep.v1 · 2026-10-15 07:17 BST');
  });

  it('counts every board as one with the decisions it answers, and every review that waits on the captain beside the decisions', async () => {
    const page = (
      await rendered(
        sitrepFixture({
          boards: [
            {
              task: 'lamp-research',
              title: 'Research the lamp',
              projects: ['harness'],
              questions: 2,
              link: 'http://127.0.0.1:4387/session/1a2b3c',
              decisions: [{ number: 'D59', title: 'Which bulb?' }],
            },
          ],
          reviews: [{ task: 'fit-lamp', title: 'Fit the lamp', projects: ['harness'], url: 'https://github.com/example-owner/harness/pull/13', afterLanding: NO_STEPS }],
        }),
      )
    ).nativeElement as HTMLElement;

    expect(texts(page, '.sr-stat__num')[0]).toBe('3');
    expect(text(page, '.sr-needs .sr-sub')).toBe('3 waiting · 1 dealt with');
    expect(texts(page, '.sr-entry__link')).toEqual(['http://127.0.0.1:4387/session/1a2b3c', 'https://github.com/example-owner/harness/pull/13']);
  });

  it('shows a decision a board answers on that board alone, never again as an entry of its own', async () => {
    const lamp = sitrepFixture().decisions[0];
    const page = (
      await rendered(
        sitrepFixture({
          decisions: [lamp, { ...lamp, number: 'D59', task: 'bulb-choice', title: 'Which bulb?' }],
          boards: [
            {
              task: 'bulb-research',
              title: 'Research the bulb',
              projects: ['harness'],
              questions: 0,
              link: 'http://127.0.0.1:4387/session/1a2b3c',
              decisions: [{ number: 'D59', title: 'Which bulb?' }],
            },
          ],
        }),
      )
    ).nativeElement as HTMLElement;

    expect(texts(page, '.sr-entry__title')).toEqual(['D58 Which lamp?', 'Board for Research the bulb', 'D57 Which horn?']);
    expect(texts(page, '.sr-entry__decision .sr-row__key')).toEqual(['D59']);
    expect(text(page, '.sr-needs .sr-sub')).toBe('2 waiting · 1 dealt with');
  });

  it('shows the captain\'s list in place of the deck, its card counter and the lines beneath it', async () => {
    const page = (await rendered()).nativeElement as HTMLElement;

    expect(page.querySelector('.sr-needs app-captains-list')).not.toBeNull();
    expect(page.querySelector('app-decision-deck')).toBeNull();
    expect(page.querySelector('app-waiting-list')).toBeNull();
    expect(page.textContent).not.toContain('card 1 of');
  });

  it('shrinks the list to a strip that says so once nothing waits and every entry is ticked off, and gives ongoing the room', async () => {
    const page = (await rendered(sitrepFixture({ decisions: [], dealtWith: { decisions: [], boards: [], reviews: [] } }))).nativeElement as HTMLElement;
    const columns = page.querySelector<HTMLElement>('.sr-cols')!;

    expect(page.querySelector('.sr-needs')?.classList).toContain('sr-section--strip');
    expect(text(page, '.sr-needs .sr-strip__words')).toBe('All ticked off · nothing needs you');
    expect(page.querySelector('app-captains-list')).toBeNull();
    expect(columns.style.getPropertyValue('--needs-track')).toBe('52px');
    expect(page.querySelector('.sr-stat')?.classList).toContain('sr-stat--call');
  });

  it('keeps an answered decision on the list until it is ticked off, and the strip once it is', async () => {
    const fixture = await rendered(sitrepFixture({ decisions: [] }));
    const page = fixture.nativeElement as HTMLElement;

    expect(text(page, '.sr-needs .sr-sub')).toBe('0 waiting · 1 dealt with');
    page.querySelector<HTMLInputElement>('.sr-entry__tick')!.click();
    http.expectOne('/api/calls/D57/tick').flush('Ticked D57 off the captain\'s list.\n');
    http.expectOne('/api/sitrep').flush(sitrepFixture({ decisions: [], dealtWith: { decisions: [], boards: [], reviews: [] } }));
    await fixture.whenStable();

    expect(page.querySelector('.sr-needs')?.classList).toContain('sr-section--strip');
  });

  it('lists a request still changing under Ongoing as coming back to the captain, never under what needs a decision', async () => {
    const page = (
      await rendered(
        sitrepFixture({
          decisions: [],
          dealtWith: { decisions: [], boards: [], reviews: [] },
          ongoing: [{ ...sitrepFixture().ongoing[0], comingBack: 'pipeline review' }],
        }),
      )
    ).nativeElement as HTMLElement;

    expect(texts(page, '.sr-stat__num')[0]).toBe('0');
    expect(page.querySelector('.sr-needs')?.classList).toContain('sr-section--strip');
    expect(text(page, '.sr-ongoing .sr-row__back')).toBe('changing, back to you when it is ready: pipeline review');
  });

  it('shows a worker question under its ongoing task as EDI\'s, in the calm tone, never as waiting on the captain', async () => {
    const page = (
      await rendered(
        sitrepFixture({
          decisions: [],
          dealtWith: { decisions: [], boards: [], reviews: [] },
          ongoing: [{ ...sitrepFixture().ongoing[0], state: 'with me', doing: 'I am settling a question' }],
          workerQuestions: [
            { task: 'fit-lamp', title: 'Fit the lamp', projects: ['harness'], question: 'Which socket does the lamp take?', askedAt: '2026-10-15T06:00:00Z' },
          ],
        }),
      )
    ).nativeElement as HTMLElement;

    expect(text(page, '.sr-ongoing .sr-badge')).toBe('with me');
    expect(page.querySelector('.sr-ongoing .sr-badge')?.classList).toContain('sr-badge--info');
    expect(text(page, '.sr-ongoing .sr-row__asked')).toBe('waiting on me for 17 minutes: Which socket does the lamp take?');
    expect(page.querySelector('.sr-needs')?.classList).toContain('sr-section--strip');
  });

  it('says the fleet stands still on the usage limit until its reset, and drops the notice once work goes on', async () => {
    const fixture = await rendered(
      sitrepFixture({
        usageLimit: { resetsAt: '2026-10-15T16:00:00Z', tasks: ['fit-lamp'] },
        ongoing: [{ ...sitrepFixture().ongoing[0], state: 'usage limit', doing: 'stopped on the session limit', limitResetsAt: '2026-10-15T16:00:00Z' }],
      }),
    );
    const page = fixture.nativeElement as HTMLElement;

    expect(text(page, '.sr-limit-stop')).toBe('The fleet stands still on the usage limit until 18:00 CEST: 1 worker waits for the reset, and I resume it then.');
    expect(page.querySelector('.sr-limit-stop + .sr-glance')).not.toBeNull();
    expect(text(page, '.sr-ongoing .sr-row__sub')).toBe('stopped on the session limit until 18:00 CEST · ship · harness + garden');

    source.listeners.get('fleet')?.();
    http.expectOne('/api/sitrep').flush(sitrepFixture());
    await fixture.whenStable();

    expect(page.querySelector('.sr-limit-stop')).toBeNull();
  });

  it('puts what needs a decision, what is ongoing and what is charted next side by side, in that order, with a border between each two', async () => {
    const page = (await rendered()).nativeElement as HTMLElement;

    expect(texts(page, '.sr-cols > .sr-section .sr-sign')).toEqual(['01Needs your decision', '02Ongoing', '03Charted next']);
    expect(Array.from(page.querySelectorAll('.sr-cols > *')).map((child) => child.tagName.toLowerCase())).toEqual([
      'section',
      'app-column-border',
      'section',
      'app-column-border',
      'section',
      'router-outlet',
    ]);
  });

  it('moves what landed recently off the sitrep to its own tab, with its count', async () => {
    const page = (await rendered()).nativeElement as HTMLElement;

    expect(page.querySelector('app-landed-list')).toBeNull();
    expect(page.textContent).not.toContain('Recently landed');
    expect(texts(page, '.sr-view')).toEqual(['Sitrep1', 'Fleet', 'Week', 'Landed1']);
  });

  it('starts what is charted next at about two thirds of its former width and folds it into a strip that shows its count', async () => {
    const fixture = await rendered();
    const page = fixture.nativeElement as HTMLElement;
    const columns = page.querySelector<HTMLElement>('.sr-cols')!;

    expect(columns.style.getPropertyValue('--charted-track')).toBe('minmax(0, 21.0%)');
    expect(columns.style.getPropertyValue('--needs-track')).toBe('minmax(0, 36.0%)');

    page.querySelector<HTMLButtonElement>('.sr-fold')!.click();
    await fixture.whenStable();

    expect(columns.style.getPropertyValue('--charted-track')).toBe('52px');
    expect(text(page, '.sr-charted .sr-strip__count')).toBe('23');
    expect(page.querySelector('app-charted-list')).toBeNull();
    expect(JSON.parse(localStorage.getItem('edi.console.sitrep-columns') ?? '{}').chartedFolded).toBe(true);
  });

  it('resizes the blocks by dragging the border between two, remembers the widths and restores the default on a double click', async () => {
    const fixture = await rendered();
    const page = fixture.nativeElement as HTMLElement;
    const columns = page.querySelector<HTMLElement>('.sr-cols')!;
    columns.getBoundingClientRect = () => ({ width: 1000 }) as DOMRect;
    const border = page.querySelector<HTMLElement>('app-column-border')!;

    border.dispatchEvent(new PointerEvent('pointerdown', { pointerId: 1, button: 0, clientX: 400 }));
    border.dispatchEvent(new PointerEvent('pointermove', { pointerId: 1, clientX: 500 }));
    border.dispatchEvent(new PointerEvent('pointerup', { pointerId: 1, clientX: 500 }));
    await fixture.whenStable();

    expect(columns.style.getPropertyValue('--needs-track')).toBe('minmax(0, 46.0%)');
    expect(JSON.parse(localStorage.getItem('edi.console.sitrep-columns') ?? '{}').needs).toBeCloseTo(0.46);

    const again = (await rendered()).nativeElement as HTMLElement;
    expect(again.querySelector<HTMLElement>('.sr-cols')!.style.getPropertyValue('--needs-track')).toBe('minmax(0, 46.0%)');

    border.dispatchEvent(new MouseEvent('dblclick'));
    await fixture.whenStable();

    expect(columns.style.getPropertyValue('--needs-track')).toBe('minmax(0, 36.0%)');
  });

  it('renders with the default widths when the browser keeps nothing', async () => {
    const reading = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('storage is off');
    });
    const writing = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('storage is off');
    });
    const fixture = await rendered();
    const page = fixture.nativeElement as HTMLElement;

    page.querySelector<HTMLButtonElement>('.sr-fold')!.click();
    await fixture.whenStable();

    expect(page.querySelector<HTMLElement>('.sr-cols')!.style.getPropertyValue('--needs-track')).toBe('minmax(0, 36.0%)');
    expect(text(page, '.sr-charted .sr-strip__count')).toBe('23');
    reading.mockRestore();
    writing.mockRestore();
  });

  it('reads the sitrep again whenever the fleet changes', async () => {
    const fixture = await rendered();

    source.listeners.get('fleet')?.();
    http.expectOne('/api/sitrep').flush(sitrepFixture({ decisions: [] }));
    await fixture.whenStable();

    expect(text(fixture.nativeElement, '.sr-needs .sr-sub')).toBe('0 waiting · 1 dealt with');
    expect(text(fixture.nativeElement, '.sr-foot .sr-meta-mono:last-child')).toBe('live · answers go straight to me');
  });

  it('shows the presence and asks EDI for present or a demo from the header', async () => {
    const fixture = await rendered();
    const page = fixture.nativeElement as HTMLElement;

    expect(text(page, '.sr-presence__mode')).toBe('quiet');
    expect(page.querySelector('.sr-presence__mode')?.getAttribute('title')).toContain('after a stretch without a typed message');
    expect(texts(page, '.sr-presence__button')).toEqual(['Present', 'Demo', 'Away']);

    (page.querySelectorAll('.sr-presence__button')[1] as HTMLButtonElement).click();
    const chosen = http.expectOne('/api/presence');
    expect(chosen.request.body).toEqual({ mode: 'demo' });
    expect(chosen.request.headers.get('X-EDI-Console')).toBe('1');
    chosen.flush('Presence: present for a demo until 2026-10-15 11:00 CEST.');
    http.expectOne('/api/sitrep').flush(sitrepFixture({ presence: { mode: 'present', demo: true, words: 'present for a demo until 2026-10-15 11:00 CEST' } }));
    await fixture.whenStable();

    expect(text(page, '.sr-presence__mode')).toBe('demo');
    expect(texts(page, '.sr-presence__button')).toEqual(['End demo']);
  });

  it('sets quiet from the header while present', async () => {
    const fixture = await rendered();
    const page = fixture.nativeElement as HTMLElement;
    http.expectNone('/api/presence');
    source.listeners.get('fleet')?.();
    http.expectOne('/api/sitrep').flush(sitrepFixture({ presence: { mode: 'present', demo: false, words: 'present' } }));
    await fixture.whenStable();

    expect(texts(page, '.sr-presence__button')).toEqual(['Demo', 'Quiet', 'Away']);
    (page.querySelectorAll('.sr-presence__button')[1] as HTMLButtonElement).click();

    expect(http.expectOne('/api/presence').request.body).toEqual({ mode: 'quiet' });
  });

  it('goes away from the header only with the captain\'s own words as its mandate', async () => {
    const fixture = await rendered();
    const page = fixture.nativeElement as HTMLElement;
    const away = Array.from(page.querySelectorAll<HTMLButtonElement>('.sr-presence__button')).find((button) => button.textContent?.trim() === 'Away');

    away?.click();
    await fixture.whenStable();
    const goAway = Array.from(page.querySelectorAll<HTMLButtonElement>('.sr-away button')).find((button) => button.textContent?.trim() === 'Go away');
    expect(goAway?.disabled).toBe(true);
    const words = page.querySelector('.sr-away__words') as HTMLInputElement;
    words.value = '  Carry on with the lamp; start nothing new.  ';
    words.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    expect(goAway?.disabled).toBe(false);
    goAway?.click();

    const chosen = http.expectOne('/api/presence');
    expect(chosen.request.body).toEqual({ mode: 'away', mandate: 'Carry on with the lamp; start nothing new.' });
    chosen.flush('Presence: away since 2026-10-15 08:20 CEST.');
    http.expectOne('/api/sitrep').flush(sitrepFixture({ presence: { mode: 'away', demo: false, words: 'away since 2026-10-15 08:20 CEST' } }));
    await fixture.whenStable();

    expect(page.querySelector('.sr-away')).toBeNull();
    expect(texts(page, '.sr-presence__button')).toEqual(['Present', 'Demo', 'Quiet']);
  });

  it('puts the links of each row under it, by its task', async () => {
    const fixture = TestBed.createComponent(SitrepPage);
    http.expectOne('/api/sitrep').flush(
      sitrepFixture({
        links: {
          'fit-lamp': { key: 'EDI-31', tracker: null, request: null, branch: { name: 'feature/EDI-31', url: 'https://github.com/example-owner/harness/tree/feature/EDI-31' },
            board: null, report: null, decisions: [] },
        },
      }),
    );
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    expect(texts(page, 'app-ongoing-list .sr-chip')).toEqual(['feature/EDI-31']);
  });

  it('opens the personality desk from the header and closes it again', async () => {
    const fixture = await rendered();
    const page = fixture.nativeElement as HTMLElement;
    const latch = page.querySelector('.sr-latch') as HTMLButtonElement;

    expect(page.querySelector('app-personality-desk')).toBeNull();
    expect(latch.getAttribute('aria-expanded')).toBe('false');

    latch.click();
    await fixture.whenStable();
    http.expectOne('/api/personality').flush(personalityFixture());
    await fixture.whenStable();

    expect(latch.getAttribute('aria-expanded')).toBe('true');
    expect(page.querySelector('.sr-main > app-personality-desk + .sr-glance')).not.toBeNull();
    expect(page.querySelectorAll('app-personality-desk .sr-slider')).toHaveLength(9);

    (Array.from(page.querySelectorAll('app-personality-desk button')).find((button) => button.textContent?.trim() === 'Close') as HTMLButtonElement).click();
    await fixture.whenStable();

    expect(page.querySelector('app-personality-desk')).toBeNull();
    expect(latch.getAttribute('aria-expanded')).toBe('false');
  });

  it('tells the open desk a demo holds once the header has asked EDI for one', async () => {
    const fixture = await rendered();
    const page = fixture.nativeElement as HTMLElement;

    (page.querySelector('.sr-latch') as HTMLButtonElement).click();
    await fixture.whenStable();
    http.expectOne('/api/personality').flush(personalityFixture());
    await fixture.whenStable();

    expect(page.textContent).not.toContain('While the demo holds');

    (page.querySelectorAll('.sr-presence__button')[1] as HTMLButtonElement).click();
    http.expectOne('/api/presence').flush('Presence: present for a demo until 2026-10-15 11:00 CEST.');
    http.expectOne('/api/sitrep').flush(sitrepFixture({ presence: { mode: 'present', demo: true, words: 'present for a demo until 2026-10-15 11:00 CEST' } }));
    await fixture.whenStable();

    expect(text(page, 'app-personality-desk .sr-desk__foot')).toContain("While the demo holds, I speak with the demo's levels on top of these.");
  });

  it('asks the captain to sign in when the console has no session', async () => {
    const fixture = TestBed.createComponent(SitrepPage);
    http.expectOne('/api/sitrep').flush('This call needs a console session.', { status: 401, statusText: 'Unauthorized' });
    await fixture.whenStable();

    expect(text(fixture.nativeElement, '.sr-notice__title')).toBe('Sign in to see the sitrep');
  });

  async function rendered(sitrep = sitrepFixture()): Promise<ComponentFixture<SitrepPage>> {
    const fixture = TestBed.createComponent(SitrepPage);
    http.expectOne('/api/sitrep').flush(sitrep);
    await fixture.whenStable();
    return fixture;
  }
});

function text(page: HTMLElement, selector: string): string {
  return page.querySelector(selector)?.textContent?.trim() ?? '';
}

function texts(page: HTMLElement, selector: string): string[] {
  return Array.from(page.querySelectorAll(selector)).map((element) => element.textContent?.replace(/\s+/g, ' ').trim() ?? '');
}
