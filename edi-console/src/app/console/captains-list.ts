import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, input, linkedSignal, output, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { refusalOf } from './answer-sentence';
import { ListEntry, callOf, opensFurther, stopsAWorker } from './captains-list-entries';
import { ConsoleApi } from './console-api';
import { DecisionCardView } from './decision-card';
import { LinkChips } from './link-chips';
import { projectsOf } from './projects';
import { AnsweredDecision, SettledBoard, TaskLinks, WaitingBoard } from './sitrep';
import { shipTime } from './stardate';
import { waitedSince } from './waited';

@Component({
  selector: 'app-captains-list',
  imports: [RouterLink, LinkChips, DecisionCardView],
  templateUrl: './captains-list.html',
  styleUrl: './captains-list.css',
})
export class CaptainsList {
  readonly entries = input.required<ListEntry[]>();
  readonly zone = input.required<string>();
  readonly generated = input.required<string>();
  readonly links = input<Record<string, TaskLinks>>({});
  readonly settled = output<void>();

  private readonly api = inject(ConsoleApi);

  protected readonly expanded = linkedSignal<ListEntry[], string | null>({
    source: this.entries,
    computation: (entries, previous) => (previous ? stillListed(entries, previous.value) : firstStopperIn(entries)),
  });
  protected readonly ticking = signal<string | null>(null);
  protected readonly refusal = signal<string | null>(null);
  protected readonly opensFurther = opensFurther;
  protected readonly projectsOf = projectsOf;

  protected toggle(entry: ListEntry): void {
    this.expanded.set(this.expanded() === entry.key ? null : entry.key);
  }

  protected tick(entry: ListEntry): void {
    const call = callOf(entry);
    if (!call || this.ticking()) {
      return;
    }
    this.refusal.set(null);
    this.ticking.set(call);
    this.api.tick(call).subscribe({
      next: () => this.ticked(),
      error: (refused: HttpErrorResponse) => this.refused(refused),
    });
  }

  protected stopsAWorker(entry: ListEntry): boolean {
    return stopsAWorker(entry);
  }

  protected waited(since: string): string {
    return waitedSince(since, this.generated());
  }

  protected boardSummary(board: WaitingBoard): string {
    const projects = projectsOf(board.projects, board.task);
    return board.questions > 0 ? `${questionsWaiting(board.questions)} · ${projects}` : projects;
  }

  protected badgeOf(board: SettledBoard): string {
    return board.state === 'round answered' ? 'answered' : 'ended';
  }

  protected targetOf(stackedOn: string | null | undefined): string {
    return stackedOn ? ` · targets ${stackedOn}, not the base: merging it does not land the work` : '';
  }

  protected given(answer: AnsweredDecision): string {
    const option = answer.option ? `option ${answer.option}, ` : '';
    return `${option}in the ${answer.channel} at ${shipTime(answer.answeredAt, this.zone())}`;
  }

  private ticked(): void {
    this.ticking.set(null);
    this.settled.emit();
  }

  private refused(response: HttpErrorResponse): void {
    this.ticking.set(null);
    this.refusal.set(refusalOf(response));
  }
}

function stillListed(entries: ListEntry[], key: string | null): string | null {
  return entries.some((entry) => entry.key === key) ? key : null;
}

function firstStopperIn(entries: ListEntry[]): string | null {
  return entries.find(stopsAWorker)?.key ?? null;
}

function questionsWaiting(count: number): string {
  return count === 1 ? '1 question waiting' : `${count} questions waiting`;
}
