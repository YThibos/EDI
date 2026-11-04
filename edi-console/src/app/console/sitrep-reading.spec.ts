import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { EVENT_SOURCES } from './fleet-stream';
import { sitrepFixture } from './sitrep.fixture';
import { SitrepReading } from './sitrep-reading';

class FakeEventSource {
  onopen: (() => void) | null = null;
  onerror: (() => void) | null = null;
  readonly listeners = new Map<string, () => void>();

  addEventListener(name: string, listener: () => void): void {
    this.listeners.set(name, listener);
  }

  close(): void {
    return;
  }
}

describe('SitrepReading', () => {
  let http: HttpTestingController;
  let source: FakeEventSource;
  let reading: SitrepReading;

  beforeEach(() => {
    source = new FakeEventSource();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), { provide: EVENT_SOURCES, useValue: () => source }, SitrepReading],
    });
    http = TestBed.inject(HttpTestingController);
    reading = TestBed.inject(SitrepReading);
  });

  afterEach(() => http.verify());

  it('reads the sitrep at once and counts what waits on the captain', () => {
    http.expectOne('/api/sitrep').flush(sitrepFixture());

    expect(reading.access()).toBe('signed-in');
    expect(reading.sitrep()?.decisions.map((card) => card.number)).toEqual(['D58']);
    expect(reading.waiting()).toBe(1);
  });

  it('reads the sitrep again on every signal of the stream and on focus, and goes live while the stream is open', () => {
    http.expectOne('/api/sitrep').flush(sitrepFixture());

    source.onopen?.();
    http.expectOne('/api/sitrep').flush(sitrepFixture());
    source.listeners.get('fleet')?.();
    http.expectOne('/api/sitrep').flush(sitrepFixture());
    window.dispatchEvent(new Event('focus'));
    http.expectOne('/api/sitrep').flush(sitrepFixture({ decisions: [] }));

    expect(reading.live()).toBe(true);
    expect(reading.waiting()).toBe(0);
  });

  it('stops being live when the stream drops, without reading again', () => {
    http.expectOne('/api/sitrep').flush(sitrepFixture());
    source.onopen?.();
    http.expectOne('/api/sitrep').flush(sitrepFixture());

    source.onerror?.();

    expect(reading.live()).toBe(false);
  });

  it('tells a missing session apart from a core that does not answer', () => {
    http.expectOne('/api/sitrep').flush('This call needs a console session.', { status: 401, statusText: 'Unauthorized' });
    const withoutSession = reading.access();
    reading.refresh();
    http.expectOne('/api/sitrep').flush('The core is down.', { status: 503, statusText: 'Service Unavailable' });

    expect(withoutSession).toBe('signed-out');
    expect(reading.access()).toBe('unreachable');
  });

  it('reads the sitrep again once the captain has chosen a presence, even when the choice fails', () => {
    http.expectOne('/api/sitrep').flush(sitrepFixture());

    reading.choosePresence('demo');
    http.expectOne('/api/presence').flush('Presence refused.', { status: 500, statusText: 'Server Error' });

    expect(http.match('/api/sitrep')).toHaveLength(1);
  });
});
