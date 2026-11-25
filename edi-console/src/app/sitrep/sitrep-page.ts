import { Component, computed, inject, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { AccessNotice } from '../console/access-notice';
import { CaptainsList } from '../console/captains-list';
import { entriesOf } from '../console/captains-list-entries';
import { ChartedList } from '../console/charted-list';
import { ConsoleHeader } from '../console/console-header';
import { HudStrip } from '../console/hud-strip';
import { OngoingList } from '../console/ongoing-list';
import { PersonalityDesk } from '../console/personality-desk';
import { LimitStop } from '../console/sitrep';
import { SitrepReading } from '../console/sitrep-reading';
import { shipMoment, shipTime } from '../console/stardate';
import { ColumnBorderHandle } from './column-border';
import { ColumnLayout } from './column-layout';

const STRIP = '52px';

@Component({
  selector: 'app-sitrep-page',
  imports: [RouterOutlet, AccessNotice, ConsoleHeader, PersonalityDesk, HudStrip, CaptainsList, OngoingList, ChartedList, ColumnBorderHandle],
  templateUrl: './sitrep-page.html',
  styleUrl: './sitrep-page.css',
  providers: [SitrepReading],
})
export class SitrepPage {
  protected readonly reading = inject(SitrepReading);
  protected readonly personalityOpen = signal(false);
  protected readonly columns = new ColumnLayout();
  protected readonly shipMoment = shipMoment;
  protected readonly entries = computed(() => {
    const sitrep = this.reading.sitrep();
    return sitrep ? entriesOf(sitrep) : [];
  });
  protected readonly nothingListed = computed(() => this.entries().length === 0);
  protected readonly listCount = computed(() => `${this.reading.waiting()} waiting · ${this.entries().filter((entry) => entry.dealtWith).length} dealt with`);
  protected readonly needsTrack = computed(() => (this.nothingListed() ? STRIP : shareOfTheRow(this.columns.needsShare())));
  protected readonly chartedTrack = computed(() => (this.columns.chartedFolded() ? STRIP : shareOfTheRow(this.columns.chartedShare())));
  protected readonly chartedShown = computed(() => {
    const sitrep = this.reading.sitrep();
    return sitrep && sitrep.chartedTotal > sitrep.charted.length ? `showing ${sitrep.charted.length} of ${sitrep.chartedTotal}` : '';
  });
  protected readonly limitNotice = computed(() => {
    const sitrep = this.reading.sitrep();
    return sitrep?.usageLimit ? standingStillOn(sitrep.usageLimit, sitrep.zone) : null;
  });
}

function shareOfTheRow(share: number): string {
  return `minmax(0, ${(share * 100).toFixed(1)}%)`;
}

function standingStillOn(limit: LimitStop, zone: string): string {
  const until = limit.resetsAt ? `until ${shipTime(limit.resetsAt, zone)}` : 'until its window resets';
  const workers = limit.tasks.length === 1 ? '1 worker waits for the reset, and I resume it' : `${limit.tasks.length} workers wait for the reset, and I resume them`;
  return `The fleet stands still on the usage limit ${until}: ${workers} then.`;
}
