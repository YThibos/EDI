import { Component, computed, effect, inject, input, signal } from '@angular/core';

import { ConsoleApi } from '../console/console-api';
import { EVENT_SOURCES } from '../console/fleet-stream';
import { TaskDetail, WorkerDetail } from '../console/task-detail';
import { Segment, segmentsOf } from './ansi';

@Component({
  selector: 'app-live-pane',
  templateUrl: './live-pane.html',
  styleUrl: './live-pane.css',
})
export class LivePane {
  readonly task = input.required<string>();
  readonly detail = input<TaskDetail | undefined>(undefined);

  private readonly api = inject(ConsoleApi);
  private readonly open = inject(EVENT_SOURCES);
  protected readonly lines = signal<Segment[][]>([]);
  protected readonly ended = signal<string | null>(null);
  protected readonly worker = signal<WorkerDetail | null>(null);
  protected readonly waitingSteers = computed(() => (this.worker()?.steers ?? []).filter((steer) => !steer.acknowledgedAt));
  protected readonly tokens = computed(() => {
    const workers = this.detail()?.cost?.workers;
    return workers ? workers.input + workers.cacheRead + workers.cacheWrite + workers.output : null;
  });

  constructor() {
    effect((onCleanup) => onCleanup(this.watch(this.task())));
    effect(() => this.readTheWorker(this.task()));
  }

  protected durationOf(iso: string): string {
    const parts = /^PT(?:(\d+)H)?(?:(\d+)M)?/.exec(iso);
    if (!parts) {
      return iso;
    }
    const minutes = Number(parts[1] ?? 0) * 60 + Number(parts[2] ?? 0);
    return `${Math.floor(minutes / 60)}h${String(minutes % 60).padStart(2, '0')}m`;
  }

  private watch(task: string): () => void {
    const source = this.open(`/api/tasks/${encodeURIComponent(task)}/live`);
    source.addEventListener('pane', (event) => this.lines.set(paneOf(event as MessageEvent<string>)));
    source.addEventListener('ended', (event) => this.end(source, (event as MessageEvent<string>).data));
    source.onerror = () => {
      if (source.readyState === EventSource.CLOSED) {
        this.ended.set(this.ended() ?? `I cannot mirror ${task}'s pane: it has no worker serving it now.`);
      }
    };
    return () => source.close();
  }

  private end(source: EventSource, why: string): void {
    this.ended.set(why);
    source.close();
  }

  private readTheWorker(task: string): void {
    this.api.worker(task).subscribe({ next: (worker) => this.worker.set(worker), error: () => this.worker.set(null) });
  }
}

function paneOf(event: MessageEvent<string>): Segment[][] {
  const pane = JSON.parse(event.data) as { lines: string[] };
  return pane.lines.map(segmentsOf);
}
