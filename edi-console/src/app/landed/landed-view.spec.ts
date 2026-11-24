import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideLocationMocks } from '@angular/common/testing';
import { TestBed } from '@angular/core/testing';
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

describe('LandedView', () => {
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

  afterEach(() => http.verify());

  it('shows the work that landed recently under its own tab, with its count on the tab, in the machine’s zone', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/landed');
    http.expectOne('/api/sitrep').flush(sitrepFixture({ landedTotal: 4 }));
    await harness.fixture.whenStable();
    const view = harness.routeNativeElement as HTMLElement;

    expect(view.querySelector('.sr-brand__sub')?.textContent).toBe('Landed');
    expect(view.querySelector('.sr-view--on')?.textContent?.trim()).toBe('Landed4');
    expect(view.querySelector('.sr-view--on .sr-view__count')?.classList).toContain('sr-view__count--calm');
    expect(view.querySelector('app-landed-list .sr-row__title')?.textContent?.trim()).toBe('Paint the buoy');
    expect(view.querySelector('app-landed-list .sr-row__sub')?.textContent).toContain('SD 2026.287 · 18:02 CEST');
    expect(view.textContent).toContain('+3 more landed this week');
    expect(document.title).toBe('(1) LANDED · EDI');
  });
});
