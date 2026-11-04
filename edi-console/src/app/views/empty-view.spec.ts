import { Location } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideLocationMocks } from '@angular/common/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { routes } from '../app.routes';
import { EVENT_SOURCES } from '../console/fleet-stream';
import { sitrepFixture } from '../console/sitrep.fixture';

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

describe('The views', () => {
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

  it('opens Fleet and Week by their addresses as empty views that still count what waits on the captain', async () => {
    const harness = await RouterTestingHarness.create();

    await harness.navigateByUrl('/fleet');
    http.expectOne('/api/sitrep').flush(sitrepFixture());
    harness.detectChanges();
    await harness.fixture.whenStable();
    const fleet = harness.routeNativeElement as HTMLElement;
    const page = harness.fixture.nativeElement as HTMLElement;

    expect(fleet.querySelector('.sr-notice__title')?.textContent).toBe('Fleet');
    expect(fleet.querySelector('.sr-notice__detail')?.textContent).toBe('Nothing here yet: this view will show the workers, my health and the usage.');
    expect(page.querySelector('.sr-view--on')?.textContent?.trim()).toBe('Fleet');
    expect(page.querySelector('.sr-view__count')?.textContent).toBe('1');
    expect(page.querySelector('.sr-latch')).toBeNull();

    await harness.navigateByUrl('/week');
    http.expectOne('/api/sitrep').flush(sitrepFixture({ decisions: [] }));
    harness.detectChanges();
    await harness.fixture.whenStable();

    expect((harness.routeNativeElement as HTMLElement).querySelector('.sr-notice__title')?.textContent).toBe('Week');
    expect(page.querySelector('.sr-view__count')).toBeNull();
  });

  it('moves between the views by their tabs, and back returns to the view before', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/fleet');
    http.expectOne('/api/sitrep').flush(sitrepFixture());
    harness.detectChanges();
    const location = TestBed.inject(Location);

    const week = Array.from((harness.fixture.nativeElement as HTMLElement).querySelectorAll<HTMLAnchorElement>('.sr-view'))
        .find((view) => view.textContent?.trim() === 'Week');
    week?.click();
    await harness.fixture.whenStable();
    http.expectOne('/api/sitrep').flush(sitrepFixture());
    const afterTheTab = location.path();
    location.back();
    await harness.fixture.whenStable();
    http.match('/api/sitrep').forEach((request) => request.flush(sitrepFixture()));

    expect(afterTheTab).toBe('/week');
    expect(location.path()).toBe('/fleet');
  });
});
