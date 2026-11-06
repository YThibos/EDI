import { Component, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { WorkedTask, WorkedWeek, hoursAndMinutes, timesheetLines, totalActive, workedOn } from './worked-week';

@Component({
  selector: 'app-week-table',
  imports: [RouterLink],
  templateUrl: './week-table.html',
  styleUrl: './week-table.css',
})
export class WeekTable {
  readonly week = input.required<WorkedWeek>();

  protected readonly hoursAndMinutes = hoursAndMinutes;
  protected readonly totalActive = totalActive;
  protected readonly workedOn = workedOn;
  protected readonly copied = signal<string | null>(null);

  protected copy(task: WorkedTask): void {
    void navigator.clipboard.writeText(timesheetLines(task)).then(() => this.copied.set(task.task));
  }

  protected dayName(day: string): string {
    return new Date(`${day}T12:00:00Z`).toLocaleDateString('en-GB', { weekday: 'short', day: 'numeric', month: 'short', timeZone: 'UTC' });
  }
}
