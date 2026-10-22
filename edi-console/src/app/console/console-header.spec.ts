import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ConsoleHeader } from './console-header';

describe('ConsoleHeader', () => {
  let fixture: ComponentFixture<ConsoleHeader>;

  beforeEach(() => {
    fixture = TestBed.createComponent(ConsoleHeader);
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
});
