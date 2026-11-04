import { Component, input } from '@angular/core';

import { projectsOf } from './projects';
import { ChartedTask, TaskLinks } from './sitrep';
import { LinkChips } from './link-chips';

@Component({
  selector: 'app-charted-list',
  imports: [LinkChips],
  templateUrl: './charted-list.html',
})
export class ChartedList {
  readonly tasks = input.required<ChartedTask[]>();
  readonly links = input<Record<string, TaskLinks>>({});
  readonly total = input.required<number>();

  protected readonly projectsOf = projectsOf;
}
