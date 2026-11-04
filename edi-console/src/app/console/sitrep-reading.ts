import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { fromEvent } from 'rxjs';

import { Access, accessRefusedBy } from './access';
import { Attention } from './attention';
import { ConsoleApi } from './console-api';
import { FleetStream, FleetStreamEvent } from './fleet-stream';
import { PresenceChoice, Sitrep } from './sitrep';
import { waitingOn } from './waiting';

@Injectable()
export class SitrepReading {
  private readonly api = inject(ConsoleApi);
  private readonly attention = inject(Attention);
  private readonly latest = signal<Sitrep | null>(null);
  private readonly granted = signal<Access>('loading');
  private readonly streaming = signal(false);

  readonly sitrep = this.latest.asReadonly();
  readonly access = this.granted.asReadonly();
  readonly live = this.streaming.asReadonly();
  readonly waiting = computed(() => waitingOn(this.sitrep()));

  constructor() {
    this.refresh();
    const stream = inject(FleetStream).events().subscribe((event) => this.follow(event));
    const focus = fromEvent(window, 'focus').subscribe(() => this.refresh());
    const firstAction = fromEvent(document, 'click').subscribe(() => this.attention.askOnce());
    inject(DestroyRef).onDestroy(() => {
      stream.unsubscribe();
      focus.unsubscribe();
      firstAction.unsubscribe();
    });
  }

  refresh(): void {
    this.api.sitrep().subscribe({
      next: (sitrep) => this.shown(sitrep),
      error: (failed) => this.granted.set(accessRefusedBy(failed)),
    });
  }

  choosePresence(mode: PresenceChoice): void {
    this.api.choosePresence(mode).subscribe({ next: () => this.refresh(), error: () => this.refresh() });
  }

  private follow(event: FleetStreamEvent): void {
    this.streaming.set(event !== 'dropped');
    if (event !== 'dropped') {
      this.refresh();
    }
  }

  private shown(sitrep: Sitrep): void {
    this.latest.set(sitrep);
    this.granted.set('signed-in');
    this.attention.follow(sitrep, this.waiting());
  }
}
