import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { fromEvent } from 'rxjs';

import { Attention } from '../console/attention';
import { ChartedList } from '../console/charted-list';
import { ConsoleApi } from '../console/console-api';
import { ConsoleHeader } from '../console/console-header';
import { DecisionDeck } from '../console/decision-deck';
import { FleetStream, FleetStreamEvent } from '../console/fleet-stream';
import { HudStrip } from '../console/hud-strip';
import { LandedList } from '../console/landed-list';
import { OngoingList } from '../console/ongoing-list';
import { PersonalityDesk } from '../console/personality-desk';
import { PresenceChoice, Sitrep } from '../console/sitrep';
import { shipMoment } from '../console/stardate';
import { waitingOn } from '../console/waiting';
import { WaitingList } from '../console/waiting-list';

type Access = 'loading' | 'signed-in' | 'signed-out' | 'unreachable';

const SIGNED_OUT = 401;

@Component({
  selector: 'app-sitrep-page',
  imports: [ConsoleHeader, PersonalityDesk, HudStrip, DecisionDeck, WaitingList, OngoingList, LandedList, ChartedList],
  templateUrl: './sitrep-page.html',
  styleUrl: './sitrep-page.css',
})
export class SitrepPage {
  private readonly api = inject(ConsoleApi);
  private readonly attention = inject(Attention);

  protected readonly sitrep = signal<Sitrep | null>(null);
  protected readonly access = signal<Access>('loading');
  protected readonly live = signal(false);
  protected readonly personalityOpen = signal(false);
  protected readonly shipMoment = shipMoment;
  protected readonly waitingCount = computed(() => waitingOn(this.sitrep()));
  protected readonly waitingOnTheCaptain = computed(() => {
    const sitrep = this.sitrep();
    if (!sitrep) {
      return '';
    }
    const waiting = this.waitingCount();
    if (waiting) {
      return `${waiting} waiting on you, ${sitrep.address}`;
    }
    return sitrep.workerQuestions.length ? `${questionsOnMe(sitrep.workerQuestions.length)}, ${sitrep.address}` : `all clear, ${sitrep.address}`;
  });
  protected readonly showsTheDeck = computed(() => {
    const sitrep = this.sitrep();
    return !!sitrep && (sitrep.decisions.length > 0 || sitrep.answered.length > 0 || (this.waitingCount() === 0 && sitrep.workerQuestions.length === 0));
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
    const firstAction = fromEvent(document, 'click').subscribe(() => this.attention.askOnce());
    destroyed.onDestroy(() => {
      stream.unsubscribe();
      focus.unsubscribe();
      firstAction.unsubscribe();
    });
  }

  protected refresh(): void {
    this.api.sitrep().subscribe({
      next: (sitrep) => this.shown(sitrep),
      error: (failed: HttpErrorResponse) => this.access.set(failed.status === SIGNED_OUT ? 'signed-out' : 'unreachable'),
    });
  }

  protected choosePresence(mode: PresenceChoice): void {
    this.api.choosePresence(mode).subscribe({ next: () => this.refresh(), error: () => this.refresh() });
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
    this.attention.follow(sitrep, this.waitingCount());
  }
}

function questionsOnMe(count: number): string {
  return count === 1 ? '1 question waiting on me' : `${count} questions waiting on me`;
}
