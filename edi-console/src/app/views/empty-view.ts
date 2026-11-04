import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { ConsoleApi } from '../console/console-api';
import { ConsoleHeader } from '../console/console-header';
import { FleetStream } from '../console/fleet-stream';
import { Sitrep } from '../console/sitrep';
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

  protected readonly data = inject(ActivatedRoute).snapshot.data as EmptyViewData;
  protected readonly sitrep = signal<Sitrep | null>(null);
  protected readonly waiting = computed(() => waitingOn(this.sitrep()));

  constructor() {
    this.refresh();
    const stream = inject(FleetStream).events().subscribe((event) => event !== 'dropped' && this.refresh());
    inject(DestroyRef).onDestroy(() => stream.unsubscribe());
  }

  protected refresh(): void {
    this.api.sitrep().subscribe({ next: (sitrep) => this.sitrep.set(sitrep), error: () => undefined });
  }

  protected choosePresence(mode: Parameters<ConsoleApi['choosePresence']>[0]): void {
    this.api.choosePresence(mode).subscribe({ next: () => this.refresh(), error: () => this.refresh() });
  }
}
