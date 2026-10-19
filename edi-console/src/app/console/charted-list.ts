import { Component, input } from '@angular/core';

import { projectsOf } from './projects';
import { ChartedTask } from './sitrep';

@Component({
  selector: 'app-charted-list',
  templateUrl: './charted-list.html',
})
export class ChartedList {
  readonly tasks = input.required<ChartedTask[]>();
  readonly total = input.required<number>();

  protected readonly projectsOf = projectsOf;
}
