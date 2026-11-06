import { Component, HostListener, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { map } from 'rxjs';

import { ConsoleApi } from '../console/console-api';
import { LinkChips } from '../console/link-chips';
import { projectsOf } from '../console/projects';
import { SitrepReading } from '../console/sitrep-reading';
import { shipMoment } from '../console/stardate';
import { AnsweredRounds, TaskDetail, TaskPageName } from '../console/task-detail';
import { toneOf } from '../console/tone';

export type PanelTab = 'timeline' | 'decisions' | 'brief' | 'report' | 'board';

interface Shown<T> {
  value?: T;
  refusal?: string;
}

const TABS: { tab: PanelTab; name: string }[] = [
  { tab: 'timeline', name: 'Timeline' },
  { tab: 'decisions', name: 'Decisions' },
  { tab: 'brief', name: 'Brief' },
  { tab: 'report', name: 'Report' },
  { tab: 'board', name: 'Board rounds' },
];

@Component({
  selector: 'app-task-panel',
  imports: [LinkChips],
  templateUrl: './task-panel.html',
  styleUrl: './task-panel.css',
  host: { class: 'sr-task-panel', role: 'dialog', '[attr.aria-label]': 'title()' },
})
export class TaskPanel {
  private readonly api = inject(ConsoleApi);
  private readonly router = inject(Router);
  private readonly reading = inject(SitrepReading);

  protected readonly tabs = TABS;
  protected readonly task = toSignal(inject(ActivatedRoute).paramMap.pipe(map((params) => params.get('id') ?? '')), { initialValue: '' });
  protected readonly tab = signal<PanelTab>('timeline');
  protected readonly detail = signal<Shown<TaskDetail>>({});
  protected readonly page = signal<Shown<string>>({});
  protected readonly rounds = signal<Shown<AnsweredRounds>>({});
  protected readonly links = computed(() => this.reading.sitrep()?.links?.[this.task()]);
  protected readonly zone = computed(() => this.reading.sitrep()?.zone ?? 'UTC');
  protected readonly title = computed(() => this.detail().value?.task.intake.title ?? this.task());
  protected readonly projectsOf = projectsOf;
  protected readonly toneOf = toneOf;
  protected readonly shipMoment = shipMoment;

  constructor() {
    effect(() => this.readTheTask(this.task(), this.reading.sitrep()?.generated));
    effect(() => this.readTheTab(this.task(), this.tab()));
  }

  @HostListener('document:keydown.escape')
  protected close(): void {
    void this.router.navigate(['/']);
  }

  protected choose(tab: PanelTab): void {
    this.tab.set(tab);
  }

  protected blockersOf(detail: TaskDetail): string {
    return detail.blockers.map((blocker) => `${blocker.id} (${blocker.state})`).join(', ');
  }

  protected wordsOf(type: string): string {
    return type.replace(/([a-z])([A-Z])/g, '$1 $2').toLowerCase();
  }

  protected durationOf(iso: string): string {
    const parts = /^PT(?:(\d+)H)?(?:(\d+)M)?/.exec(iso);
    if (!parts) {
      return iso;
    }
    const minutes = Number(parts[1] ?? 0) * 60 + Number(parts[2] ?? 0);
    return `${Math.floor(minutes / 60)}h${String(minutes % 60).padStart(2, '0')}m`;
  }

  private readTheTask(task: string, _generated: string | undefined): void {
    if (!task) {
      return;
    }
    this.api.task(task).subscribe({
      next: (detail) => this.detail.set({ value: detail }),
      error: (failed) => this.detail.set({ refusal: refusalOf(failed, `I could not read ${task}.`) }),
    });
  }

  private readTheTab(task: string, tab: PanelTab): void {
    if (!task) {
      return;
    }
    if (tab === 'brief' || tab === 'report') {
      this.readThePage(task, tab);
    } else if (tab === 'board') {
      this.rounds.set({});
      this.api.boardRounds(task).subscribe({
        next: (rounds) => this.rounds.set({ value: rounds }),
        error: (failed) => this.rounds.set({ refusal: refusalOf(failed, `${task} has no board round.`) }),
      });
    }
  }

  private readThePage(task: string, page: TaskPageName): void {
    this.page.set({});
    this.api.taskPage(task, page).subscribe({
      next: (html) => this.page.set({ value: bodyOf(html) }),
      error: (failed) => this.page.set({ refusal: refusalOf(failed, `${task} has no ${page} yet.`) }),
    });
  }
}

function bodyOf(html: string): string {
  return new DOMParser().parseFromString(html, 'text/html').body.innerHTML;
}

function refusalOf(failed: { error?: unknown }, otherwise: string): string {
  return typeof failed.error === 'string' && failed.error.trim() ? failed.error.trim() : otherwise;
}
