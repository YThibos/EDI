import { Component, input } from '@angular/core';

import { projectsOf } from './projects';
import { OngoingTask } from './sitrep';
import { toneOf } from './tone';

@Component({
  selector: 'app-ongoing-list',
  templateUrl: './ongoing-list.html',
})
export class OngoingList {
  readonly tasks = input.required<OngoingTask[]>();

  protected readonly projectsOf = projectsOf;
  protected readonly toneOf = toneOf;
}
