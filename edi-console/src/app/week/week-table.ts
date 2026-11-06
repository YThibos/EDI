import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { WorkedWeek, hoursAndMinutes, totalActive, workedOn } from './worked-week';

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

  protected dayName(day: string): string {
    return new Date(`${day}T12:00:00Z`).toLocaleDateString('en-GB', { weekday: 'short', day: 'numeric', month: 'short', timeZone: 'UTC' });
  }
}
