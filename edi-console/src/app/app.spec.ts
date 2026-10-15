import { TestBed } from '@angular/core/testing';
import { App } from './app';

describe('App', () => {
  it('shows the EDI wordmark', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    expect(page.querySelector('h1')?.textContent).toBe('EDI');
    expect(page.querySelector('p')?.textContent).toBe('Enhanced Development Intelligence');
  });
});
