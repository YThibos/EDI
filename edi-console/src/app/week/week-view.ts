import { Component, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { map } from 'rxjs';

import { AccessNotice } from '../console/access-notice';
import { ConsoleApi } from '../console/console-api';
import { ConsoleHeader } from '../console/console-header';
import { SitrepReading } from '../console/sitrep-reading';
import { WeekTable } from './week-table';
import { WorkedWeek, weekAround } from './worked-week';

@Component({
  selector: 'app-week-view',
  imports: [AccessNotice, ConsoleHeader, RouterLink, WeekTable],
  templateUrl: './week-view.html',
  styleUrl: './week-view.css',
  providers: [SitrepReading],
})
export class WeekView {
  protected readonly reading = inject(SitrepReading);
  protected readonly week = signal<WorkedWeek | null>(null);
  protected readonly weekAround = weekAround;
  private readonly api = inject(ConsoleApi);
  private readonly from = toSignal(inject(ActivatedRoute).queryParamMap.pipe(map((params) => params.get('from'))), { initialValue: null });

  constructor() {
    effect(() => this.readTheWeek(this.from(), this.reading.sitrep()?.generated));
  }

  protected dayOf(day: string): string {
    return new Date(`${day}T12:00:00Z`).toLocaleDateString('en-GB', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' });
  }

  private readTheWeek(from: string | null, generated: string | undefined): void {
    if (!generated) {
      return;
    }
    this.api.week(from).subscribe({ next: (week) => this.week.set(week), error: () => this.week.set(null) });
  }
}
