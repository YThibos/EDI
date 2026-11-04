import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { fromEvent } from 'rxjs';

import { Access, accessRefusedBy } from '../console/access';
import { Attention } from '../console/attention';
import { ConsoleApi } from '../console/console-api';
import { ConsoleHeader } from '../console/console-header';
import { FleetStream } from '../console/fleet-stream';
import { PresenceChoice, Sitrep } from '../console/sitrep';
import { waitingOn } from '../console/waiting';

export interface EmptyViewData {
  view: string;
  shows: string;
}

@Component({
  selector: 'app-empty-view',
  imports: [ConsoleHeader],
  templateUrl: './empty-view.html',
})
export class EmptyView {
  private readonly api = inject(ConsoleApi);
  private readonly attention = inject(Attention);

  protected readonly data = inject(ActivatedRoute).snapshot.data as EmptyViewData;
  protected readonly sitrep = signal<Sitrep | null>(null);
  protected readonly access = signal<Access>('loading');
  protected readonly waiting = computed(() => waitingOn(this.sitrep()));

  constructor() {
    this.refresh();
    const stream = inject(FleetStream).events().subscribe((event) => event !== 'dropped' && this.refresh());
    const focus = fromEvent(window, 'focus').subscribe(() => this.refresh());
    const firstAction = fromEvent(document, 'click').subscribe(() => this.attention.askOnce());
    inject(DestroyRef).onDestroy(() => {
      stream.unsubscribe();
      focus.unsubscribe();
      firstAction.unsubscribe();
    });
  }

  protected refresh(): void {
    this.api.sitrep().subscribe({
      next: (sitrep) => this.shown(sitrep),
      error: (failed) => this.access.set(accessRefusedBy(failed)),
    });
  }

  protected choosePresence(mode: PresenceChoice): void {
    this.api.choosePresence(mode).subscribe({ next: () => this.refresh(), error: () => this.refresh() });
  }

  private shown(sitrep: Sitrep): void {
    this.sitrep.set(sitrep);
    this.access.set('signed-in');
    this.attention.follow(sitrep, this.waiting());
  }
}
