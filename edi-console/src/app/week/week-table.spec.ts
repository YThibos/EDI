import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { WeekTable } from './week-table';
import { WorkedWeek } from './worked-week';

const WEEK: WorkedWeek = {
  from: '2026-10-12',
  zone: 'Europe/Brussels',
  days: ['2026-10-12', '2026-10-13', '2026-10-14', '2026-10-15', '2026-10-16', '2026-10-17', '2026-10-18'],
  tasks: [
    {
      task: 'fit-lamp',
      title: 'Fit the lamp',
      key: 'HRN-3',
      bookedOn: 'SVC-2',
      days: [
        { day: '2026-10-15', activeMinutes: 30, wallClockMinutes: 40 },
        { day: '2026-10-16', activeMinutes: 45, wallClockMinutes: 75 },
      ],
    },
  ],
};

describe('WeekTable', () => {
  let fixture: ComponentFixture<WeekTable>;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    fixture = TestBed.createComponent(WeekTable);
  });

  it('shows each task with the ticket it books on, its active of wall-clock time per day and its week total', async () => {
    const table = await shown(WEEK);

    expect(texts(table, 'thead th')).toEqual(['Task', 'Booked on', 'Mon 12 Oct', 'Tue 13 Oct', 'Wed 14 Oct', 'Thu 15 Oct', 'Fri 16 Oct', 'Sat 17 Oct', 'Sun 18 Oct', 'Week']);
    expect(texts(table, 'tbody tr:first-child > *')).toEqual(['Fit the lamp HRN-3', 'SVC-2', '', '', '', '0h30m of 0h40m', '0h45m of 1h15m', '', '', '1h15m']);
    expect(table.querySelector('.sr-row__open')?.getAttribute('href')).toBe('/task/fit-lamp');
  });

  it('says so when no task had active time in the week', async () => {
    const table = await shown({ ...WEEK, tasks: [] });

    expect(table.querySelector('table')).toBeNull();
    expect(table.textContent?.trim()).toBe('No task had active time this week.');
  });

  async function shown(week: WorkedWeek): Promise<HTMLElement> {
    fixture.componentRef.setInput('week', week);
    await fixture.whenStable();
    return fixture.nativeElement;
  }
});

function texts(root: HTMLElement, selector: string): string[] {
  return Array.from(root.querySelectorAll(selector)).map((element) => element.textContent?.replace(/\s+/g, ' ').trim() ?? '');
}
