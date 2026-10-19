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
    expect(page.querySelector('.sr-clock__sd')?.textContent).toBe('SD 2026.288 · 06:17Z');
    expect(page.querySelector('.sr-brand__wm')?.textContent).toBe('EDI');
    expect(text(page, '.sr-section__head .sr-sub')).toBe('1 waiting on you, Commander');
    expect(text(page, '.sr-decision__title')).toBe('Which lamp?');
    expect(texts(page, '.sr-row__title')).toEqual(['Fit the lamp', 'Paint the buoy', 'Tune the horn', 'Scrub the deck']);
    expect(text(page, '.sr-row__pr')).toBe('#12');
    expect(page.textContent).toContain('showing 2 of 23');
    expect(page.textContent).toContain('+21 more queued');
  });

  it('reads the sitrep again whenever the fleet changes', async () => {
    const fixture = await rendered();

    source.listeners.get('fleet')?.();
    http.expectOne('/api/sitrep').flush(sitrepFixture({ decisions: [] }));
    await fixture.whenStable();

    expect(text(fixture.nativeElement, '.sr-section__head .sr-sub')).toBe('all clear, Commander');
    expect(text(fixture.nativeElement, '.sr-foot .sr-meta-mono:last-child')).toBe('live · answers go straight to EDI');
  });

  it('asks the captain to sign in when the console has no session', async () => {
    const fixture = TestBed.createComponent(SitrepPage);
    http.expectOne('/api/sitrep').flush('This call needs a console session.', { status: 401, statusText: 'Unauthorized' });
    await fixture.whenStable();

    expect(text(fixture.nativeElement, '.sr-notice__title')).toBe('Sign in to see the sitrep');
  });

  async function rendered(): Promise<ComponentFixture<SitrepPage>> {
    const fixture = TestBed.createComponent(SitrepPage);
    http.expectOne('/api/sitrep').flush(sitrepFixture());
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
