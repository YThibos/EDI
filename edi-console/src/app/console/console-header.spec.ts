import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { ConsoleHeader } from './console-header';

describe('ConsoleHeader', () => {
  let fixture: ComponentFixture<ConsoleHeader>;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    fixture = TestBed.createComponent(ConsoleHeader);
    fixture.componentRef.setInput('view', 'Sitrep');
  });

  it('shows the stardate clock in the machine’s zone, with the day apart so a phone can leave it out', async () => {
    fixture.componentRef.setInput('generated', '2026-10-25T01:30:00Z');
    fixture.componentRef.setInput('zone', 'Europe/Brussels');
    await fixture.whenStable();
    const header: HTMLElement = fixture.nativeElement;

    expect(header.querySelector('.sr-clock__sd')?.textContent).toBe('SD 2026.298 · 02:30 CET');
    expect(header.querySelector('.sr-clock__day')?.textContent).toBe('SD 2026.298 · ');
  });

  it('shows a dash before the core has said what time it is', async () => {
    await fixture.whenStable();
    const header: HTMLElement = fixture.nativeElement;

    expect(header.querySelector('.sr-clock__sd')?.textContent).toBe('-');
    expect(header.querySelector('.sr-clock__day')).toBeNull();
  });

  it('offers the three views as tabs, the Sitrep tab counting what waits on the captain', async () => {
    fixture.componentRef.setInput('waiting', 3);
    await fixture.whenStable();
    const header: HTMLElement = fixture.nativeElement;

    expect(Array.from(header.querySelectorAll('.sr-view')).map((view) => [view.textContent?.trim(), view.getAttribute('href')])).toEqual([
      ['Sitrep3', '/'],
      ['Fleet', '/fleet'],
      ['Week', '/week'],
    ]);
    expect(header.querySelector('.sr-view__count')?.textContent).toBe('3');
  });

  it('names the view on screen beside the wordmark', async () => {
    fixture.componentRef.setInput('view', 'Fleet');
    await fixture.whenStable();

    expect((fixture.nativeElement as HTMLElement).querySelector('h1.sr-brand__sub')?.textContent).toBe('Fleet');
  });

  it('gives the view’s name one width whichever view is on screen, so the tabs keep their place', async () => {
    const widths: string[] = [];
    for (const view of ['Sitrep', 'Fleet', 'Week', 'Sign in']) {
      fixture.componentRef.setInput('view', view);
      await fixture.whenStable();
      widths.push(getComputedStyle(subtitle()).width);
    }

    expect(widths[0]).not.toMatch(/^(auto)?$/);
    expect(new Set(widths).size).toBe(1);
  });

  it('keeps the tabs beside the brand and the presence beside the clock, signed in or out', async () => {
    await fixture.whenStable();
    expect(getComputedStyle(views()).marginRight).toBe('auto');

    fixture.componentRef.setInput('presence', { mode: 'present', demo: false, words: 'Present' });
    await fixture.whenStable();
    expect(getComputedStyle(views()).marginRight).toBe('auto');
    expect(getComputedStyle(presenceBlock()).marginLeft).not.toBe('auto');
  });

  it('leaves the count out while nothing waits', async () => {
    await fixture.whenStable();

    expect((fixture.nativeElement as HTMLElement).querySelector('.sr-view__count')).toBeNull();
  });

  function subtitle(): HTMLElement {
    return (fixture.nativeElement as HTMLElement).querySelector('.sr-brand__sub')!;
  }

  function views(): HTMLElement {
    return (fixture.nativeElement as HTMLElement).querySelector('.sr-views')!;
  }

  function presenceBlock(): HTMLElement {
    return (fixture.nativeElement as HTMLElement).querySelector('.sr-presence')!;
  }
});
