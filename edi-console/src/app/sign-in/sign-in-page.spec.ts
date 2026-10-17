import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { SignInPage } from './sign-in-page';

describe('SignInPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    history.replaceState(null, '', '/');
  });

  it('trades the token of its link for a session, hides it, and opens the sitrep', async () => {
    history.replaceState(null, '', '/login#one-time-token');
    const navigation = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);

    const fixture = TestBed.createComponent(SignInPage);
    const signIn = http.expectOne('/api/console/session');
    signIn.flush('');
    await fixture.whenStable();

    expect(signIn.request.body).toEqual({ token: 'one-time-token' });
    expect(signIn.request.headers.get('X-EDI-Console')).toBe('1');
    expect(window.location.hash).toBe('');
    expect(navigation).toHaveBeenCalledWith('/', { replaceUrl: true });
  });

  it('says plainly when the link was used or has expired', async () => {
    history.replaceState(null, '', '/login#spent-token');

    const fixture = TestBed.createComponent(SignInPage);
    http.expectOne('/api/console/session').flush('This sign-in link was used already or has expired.', { status: 401, statusText: 'Unauthorized' });
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('.sr-notice__title')?.textContent).toBe('This sign-in link no longer works');
  });
});
