import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EVENT_SOURCES } from '../console/fleet-stream';
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

    expect(texts(page, '.sr-stat__num')).toEqual(['1', '1', '1', '23']);
    expect(page.querySelector('.sr-stat')?.classList).toContain('sr-stat--hot');
    expect(page.querySelector('.sr-clock__sd')?.textContent).toBe('SD 2026.288 · 08:17 CEST');
    expect(page.querySelector('.sr-brand__wm')?.textContent).toBe('EDI');
    expect(text(page, '.sr-section__head .sr-sub')).toBe('1 waiting on you, Commander');
    expect(text(page, '.sr-decision__title')).toBe('Which lamp?');
    expect(texts(page, '.sr-row__title')).toEqual(['Fit the lamp', 'Tune the horn', 'Scrub the deck', 'Paint the buoy']);
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

  it('puts what needs a decision, what is ongoing and what is charted next side by side, in that order', async () => {
    const page = (await rendered()).nativeElement as HTMLElement;

    expect(texts(page, '.sr-cols > .sr-section .sr-sign')).toEqual(['01Needs your decision', '02Ongoing', '03Charted next']);
    expect(Array.from(page.querySelectorAll('.sr-cols > .sr-section .sr-section__body')).map((body) => body.tagName.toLowerCase())).toEqual([
      'app-decision-deck',
      'app-ongoing-list',
      'app-charted-list',
    ]);
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
    expect(text(fixture.nativeElement, '.sr-foot .sr-meta-mono:last-child')).toBe('live · answers go straight to EDI');
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
