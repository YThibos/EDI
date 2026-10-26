import { Component, input } from '@angular/core';

import { projectsOf } from './projects';
import { ChangingRequest, ReadyForReview, WaitingBoard } from './sitrep';

@Component({
  selector: 'app-waiting-list',
  templateUrl: './waiting-list.html',
  styleUrl: './waiting-list.css',
})
export class WaitingList {
  readonly boards = input.required<WaitingBoard[]>();
  readonly reviews = input.required<ReadyForReview[]>();
  readonly changing = input.required<ChangingRequest[]>();

  protected readonly projectsOf = projectsOf;

  protected boardSummary(board: WaitingBoard): string {
    const projects = projectsOf(board.projects, board.task);
    return board.questions > 0 ? `${questionsWaiting(board.questions)} · ${projects}` : projects;
  }
}

function questionsWaiting(count: number): string {
  return count === 1 ? '1 question waiting' : `${count} questions waiting`;
}
