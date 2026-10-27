import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

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

describe('SitrepPage', () => {
  let http: HttpTestingController;
  let source: FakeEventSource;

  beforeEach(() => {
    source = new FakeEventSource();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: EVENT_SOURCES, useValue: () => source }],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('shows the fleet in the approved board sections', async () => {
    const page = (await rendered()).nativeElement as HTMLElement;

    expect(page.querySelector('app-waiting-list')).toBeNull();

    expect(texts(page, '.sr-stat__num')).toEqual(['1', '1', '1', '23']);
    expect(page.querySelector('.sr-stat')?.classList).toContain('sr-stat--hot');
    expect(page.querySelector('.sr-clock__sd')?.textContent).toBe('SD 2026.288 · 08:17 CEST');
    expect(page.querySelector('.sr-brand__wm')?.textContent).toBe('EDI');
    expect(text(page, '.sr-section__head .sr-sub')).toBe('1 waiting on you, Commander');
    expect(text(page, '.sr-decision__title')).toBe('Which lamp?');
    expect(texts(page, '.sr-row__title')).toEqual(['EDI-31 Fit the lamp', 'Tune the horn', 'Scrub the deck', 'Paint the buoy']);
    expect(text(page, '.sr-row__pr')).toBe('#12');
    expect(page.textContent).toContain('showing 2 of 23');
    expect(page.textContent).toContain('+21 more queued');
  });

  it('shows every time on the page in the machine’s zone that the core names', async () => {
    const page = (await rendered(sitrepFixture({ zone: 'Europe/London' }))).nativeElement as HTMLElement;

    expect(page.querySelector('.sr-clock__sd')?.textContent).toBe('SD 2026.288 · 07:17 BST');
    expect(text(page, 'app-landed-list .sr-row__sub')).toContain('SD 2026.287 · 17:02 BST');
    expect(text(page, '.sr-foot .sr-meta-mono')).toBe('edi-sitrep.v1 · 2026-10-15 07:17 BST');
  });

  it('counts every board, the decisions it answers, and every review that waits on the captain beside the decisions', async () => {
    const fixture = TestBed.createComponent(SitrepPage);
    http.expectOne('/api/sitrep').flush(
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
        reviews: [{ task: 'fit-lamp', title: 'Fit the lamp', projects: ['harness'], url: 'https://github.com/example-owner/harness/pull/13' }],
      }),
    );
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    expect(texts(page, '.sr-stat__num')[0]).toBe('4');
    expect(text(page, '.sr-section__head .sr-sub')).toBe('4 waiting on you, Commander');
    expect(text(page, '.sr-decision__title')).toBe('Which lamp?');
    expect(texts(page, '.sr-waiting__link')).toEqual(['http://127.0.0.1:4387/session/1a2b3c', 'https://github.com/example-owner/harness/pull/13']);
  });

  it('counts only the decisions a board answers when it asks no question of its own', async () => {
    const fixture = TestBed.createComponent(SitrepPage);
    http.expectOne('/api/sitrep').flush(
      sitrepFixture({
        boards: [
          {
            task: 'lamp-research',
            title: 'Research the lamp',
            projects: ['harness'],
            questions: 0,
            link: 'http://127.0.0.1:4387/session/1a2b3c',
            decisions: [{ number: 'D59', title: 'Which bulb?' }],
          },
        ],
      }),
    );
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    expect(text(page, '.sr-section__head .sr-sub')).toBe('2 waiting on you, Commander');
  });

  it('never calls the captain all clear while a board or a review waits on him', async () => {
    const fixture = TestBed.createComponent(SitrepPage);
    http.expectOne('/api/sitrep').flush(
      sitrepFixture({
        decisions: [],
        answered: [],
        reviews: [{ task: 'fit-lamp', title: 'Fit the lamp', projects: ['harness'], url: 'https://github.com/example-owner/harness/pull/13' }],
      }),
    );
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    expect(text(page, '.sr-section__head .sr-sub')).toBe('1 waiting on you, Commander');
    expect(page.querySelector('.sr-clear')).toBeNull();
    expect(page.querySelector('.sr-stat')?.classList).toContain('sr-stat--hot');
    expect(page.querySelector('.sr-waiting-on-you > app-waiting-list')).not.toBeNull();
  });

  it('counts a request still changing after its done and lists it in the zone, never as all clear', async () => {
    const fixture = TestBed.createComponent(SitrepPage);
    http.expectOne('/api/sitrep').flush(
      sitrepFixture({
        decisions: [],
        answered: [],
        changing: [
          { task: 'fit-lamp', title: 'Fit the lamp', projects: ['harness'], url: 'https://github.com/example-owner/harness/pull/13', why: 'pipeline review' },
        ],
      }),
    );
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    expect(text(page, '.sr-section__head .sr-sub')).toBe('1 waiting on you, Commander');
    expect(page.querySelector('.sr-clear')).toBeNull();
    expect(texts(page, '.sr-waiting-on-you .sr-badge')).toEqual(['changing']);
  });

  it('shows a worker question as EDI\'s, in the calm tone, never as waiting on the captain', async () => {
    const fixture = TestBed.createComponent(SitrepPage);
    http.expectOne('/api/sitrep').flush(
      sitrepFixture({
        decisions: [],
        answered: [],
        ongoing: [
          { task: 'fit-lamp', key: null, title: 'Fit the lamp', kind: 'ship', projects: ['harness'], state: 'with me', doing: 'I am settling a question', draft: null },
        ],
      }),
    );
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    expect(text(page, '.sr-row .sr-badge')).toBe('with me');
    expect(page.querySelector('.sr-row .sr-badge')?.classList).toContain('sr-badge--info');
    expect(text(page, '.sr-row__sub')).toContain('I am settling a question');
    expect(text(page, '.sr-section__head .sr-sub')).toBe('all clear, Commander');
  });

  it('puts what needs a decision, what is ongoing and what is charted next side by side, in that order', async () => {
    const page = (await rendered()).nativeElement as HTMLElement;

    expect(texts(page, '.sr-cols > .sr-section .sr-sign')).toEqual(['01Needs your decision', '02Ongoing', '03Charted next']);
    expect(Array.from(page.querySelectorAll('.sr-cols > .sr-section > .sr-section__body')).map((body) => body.tagName.toLowerCase())).toEqual([
      'div',
      'app-ongoing-list',
      'app-charted-list',
    ]);
    expect(page.querySelector('.sr-waiting-on-you > app-decision-deck')).not.toBeNull();
  });

  it('keeps what landed recently below the columns, last', async () => {
    const page = (await rendered()).nativeElement as HTMLElement;
    const signs = texts(page, '.sr-sign');

    expect(signs).toEqual(['01Needs your decision', '02Ongoing', '03Charted next', '04Recently landed']);
    expect(page.querySelector('.sr-cols app-landed-list')).toBeNull();
    expect(page.querySelector('.sr-glance + .sr-section app-landed-list')).not.toBeNull();
  });

  it('reads the sitrep again whenever the fleet changes', async () => {
    const fixture = await rendered();

    source.listeners.get('fleet')?.();
    http.expectOne('/api/sitrep').flush(sitrepFixture({ decisions: [] }));
    await fixture.whenStable();

    expect(text(fixture.nativeElement, '.sr-section__head .sr-sub')).toBe('all clear, Commander');
    expect(text(fixture.nativeElement, '.sr-foot .sr-meta-mono:last-child')).toBe('live · answers go straight to me');
  });

  it('shows the presence and asks EDI for present or a demo from the header', async () => {
    const fixture = await rendered();
    const page = fixture.nativeElement as HTMLElement;

    expect(text(page, '.sr-presence__mode')).toBe('quiet');
    expect(page.querySelector('.sr-presence__mode')?.getAttribute('title')).toContain('after a stretch without a typed message');
    expect(texts(page, '.sr-presence__button')).toEqual(['Present', 'Demo']);

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
  return Array.from(page.querySelectorAll(selector)).map((element) => element.textContent?.trim() ?? '');
}
