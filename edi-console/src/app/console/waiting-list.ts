import { Component, input } from '@angular/core';

import { projectsOf } from './projects';
import { ReadyForReview, WaitingBoard } from './sitrep';

@Component({
  selector: 'app-waiting-list',
  templateUrl: './waiting-list.html',
  styleUrl: './waiting-list.css',
})
export class WaitingList {
  readonly boards = input.required<WaitingBoard[]>();
  readonly reviews = input.required<ReadyForReview[]>();

  protected readonly projectsOf = projectsOf;

  protected questions(count: number): string {
    return count === 1 ? '1 question waiting' : `${count} questions waiting`;
  }
}
