import { Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { projectsOf } from './projects';
import { OngoingTask, TaskLinks, WorkerQuestion } from './sitrep';
import { LinkChips } from './link-chips';
import { shipTime } from './stardate';
import { toneOf } from './tone';
import { waitedSince } from './waited';

@Component({
  selector: 'app-ongoing-list',
  imports: [RouterLink, LinkChips],
  templateUrl: './ongoing-list.html',
})
export class OngoingList {
  readonly tasks = input.required<OngoingTask[]>();
  readonly links = input<Record<string, TaskLinks>>({});
  readonly workerQuestions = input<WorkerQuestion[]>([]);
  readonly zone = input('');
  readonly generated = input<string | null>(null);

  protected readonly projectsOf = projectsOf;
  protected readonly toneOf = toneOf;
  protected readonly questionOf = computed(() => new Map(this.workerQuestions().map((question) => [question.task, question])));

  protected doing(ongoing: OngoingTask): string {
    return ongoing.limitResetsAt ? `${ongoing.doing} until ${shipTime(ongoing.limitResetsAt, this.zone())}` : ongoing.doing;
  }

  protected askedOfMe(question: WorkerQuestion): string {
    return `waiting on me for ${waitedSince(question.askedAt, this.generated())}: ${question.question}`;
  }
}
