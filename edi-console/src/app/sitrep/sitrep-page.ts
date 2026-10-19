import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { fromEvent } from 'rxjs';

import { ChartedList } from '../console/charted-list';
import { ConsoleApi } from '../console/console-api';
import { ConsoleHeader } from '../console/console-header';
import { DecisionDeck } from '../console/decision-deck';
import { FleetStream, FleetStreamEvent } from '../console/fleet-stream';
import { HudStrip } from '../console/hud-strip';
import { LandedList } from '../console/landed-list';
import { OngoingList } from '../console/ongoing-list';
import { Sitrep } from '../console/sitrep';

type Access = 'loading' | 'signed-in' | 'signed-out' | 'unreachable';

const SIGNED_OUT = 401;

@Component({
  selector: 'app-sitrep-page',
  imports: [ConsoleHeader, HudStrip, DecisionDeck, OngoingList, LandedList, ChartedList],
  templateUrl: './sitrep-page.html',
  styleUrl: './sitrep-page.css',
})
export class SitrepPage {
  private readonly api = inject(ConsoleApi);

  protected readonly sitrep = signal<Sitrep | null>(null);
  protected readonly access = signal<Access>('loading');
  protected readonly live = signal(false);
  protected readonly waitingOnTheCaptain = computed(() => {
    const sitrep = this.sitrep();
    if (!sitrep) {
      return '';
    }
    const open = sitrep.decisions.length;
    return open ? `${open} waiting on you, ${sitrep.address}` : `all clear, ${sitrep.address}`;
  });
  protected readonly chartedShown = computed(() => {
    const sitrep = this.sitrep();
    return sitrep && sitrep.chartedTotal > sitrep.charted.length ? `showing ${sitrep.charted.length} of ${sitrep.chartedTotal}` : '';
  });

  constructor() {
    this.refresh();
    const destroyed = inject(DestroyRef);
    const stream = inject(FleetStream).events().subscribe((event) => this.follow(event));
    const focus = fromEvent(window, 'focus').subscribe(() => this.refresh());
    destroyed.onDestroy(() => {
      stream.unsubscribe();
      focus.unsubscribe();
    });
  }

  protected refresh(): void {
    this.api.sitrep().subscribe({
      next: (sitrep) => this.shown(sitrep),
      error: (failed: HttpErrorResponse) => this.access.set(failed.status === SIGNED_OUT ? 'signed-out' : 'unreachable'),
    });
  }

  private follow(event: FleetStreamEvent): void {
    this.live.set(event !== 'dropped');
    if (event !== 'dropped') {
      this.refresh();
    }
  }

  private shown(sitrep: Sitrep): void {
    this.sitrep.set(sitrep);
    this.access.set('signed-in');
  }
}
