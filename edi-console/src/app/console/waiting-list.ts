import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { projectsOf } from './projects';
import { ChangingRequest, ReadyForReview, TaskLinks, WaitingBoard, WorkerQuestion } from './sitrep';
import { LinkChips } from './link-chips';

@Component({
  selector: 'app-waiting-list',
  imports: [RouterLink, LinkChips],
  templateUrl: './waiting-list.html',
  styleUrl: './waiting-list.css',
})
export class WaitingList {
  readonly boards = input.required<WaitingBoard[]>();
  readonly reviews = input.required<ReadyForReview[]>();
  readonly changing = input.required<ChangingRequest[]>();
  readonly links = input<Record<string, TaskLinks>>({});
  readonly workerQuestions = input<WorkerQuestion[]>([]);
  readonly generated = input<string | null>(null);

  protected readonly projectsOf = projectsOf;

  protected boardSummary(board: WaitingBoard): string {
    const projects = projectsOf(board.projects, board.task);
    return board.questions > 0 ? `${questionsWaiting(board.questions)} · ${projects}` : projects;
  }

  protected questionSummary(question: WorkerQuestion): string {
    return `waiting on me for ${waitedSince(question.askedAt, this.generated())}: ${question.question} · ${projectsOf(question.projects, question.task)}`;
  }
}

function waitedSince(askedAt: string, generated: string | null): string {
  const minutes = Math.max(0, Math.floor(((generated ? Date.parse(generated) : Date.now()) - Date.parse(askedAt)) / 60000));
  if (minutes === 0) {
    return 'under a minute';
  }
  if (minutes < 60) {
    return counted(minutes, 'minute');
  }
  const rest = minutes % 60;
  return counted(Math.floor(minutes / 60), 'hour') + (rest ? ` ${counted(rest, 'minute')}` : '');
}

function counted(count: number, noun: string): string {
  return `${count} ${noun}${count === 1 ? '' : 's'}`;
}

function questionsWaiting(count: number): string {
  return count === 1 ? '1 question waiting' : `${count} questions waiting`;
}
