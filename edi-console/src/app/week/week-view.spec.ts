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
import { WorkedWeek } from './worked-week';

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

function weekFrom(from: string, thisWeek = '2026-11-02'): WorkedWeek {
  return { from, thisWeek, zone: 'Europe/Brussels', days: [from], tasks: [] };
}

describe('WeekView', () => {
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

  it('opens on the current week the core names, and steps to the week before but not past this week', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/week');
    http.expectOne('/api/sitrep').flush(sitrepFixture());
    await harness.fixture.whenStable();
    const asked = http.expectOne((request) => request.url === '/api/week');
    asked.flush(weekFrom('2026-11-02'));
    await harness.fixture.whenStable();
    const view = harness.routeNativeElement as HTMLElement;

    expect(asked.request.params.has('from')).toBe(false);
    expect(view.querySelector('h2.sr-sign')?.textContent).toBe('Week of 2 November 2026');
    expect(steps(view)).toEqual([['Week before', '/week?from=2026-10-26']]);
    expect(document.title).toBe('(1) WEEK · EDI');
  });

  it('opens the week its address names, with a step back to the week after', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/week?from=2026-10-26');
    http.expectOne('/api/sitrep').flush(sitrepFixture());
    await harness.fixture.whenStable();
    const asked = http.expectOne((request) => request.url === '/api/week');
    asked.flush(weekFrom('2026-10-26'));
    await harness.fixture.whenStable();

    expect(asked.request.params.get('from')).toBe('2026-10-26');
    expect(steps(harness.routeNativeElement as HTMLElement)).toEqual([
      ['Week before', '/week?from=2026-10-19'],
      ['Week after', '/week?from=2026-11-02'],
    ]);
    expect(TestBed.inject(Location).path()).toBe('/week?from=2026-10-26');
  });
});

function steps(view: HTMLElement): string[][] {
  return Array.from(view.querySelectorAll<HTMLAnchorElement>('.sr-weekhead__step')).map((step) => [step.textContent?.trim() ?? '', step.getAttribute('href') ?? '']);
}
