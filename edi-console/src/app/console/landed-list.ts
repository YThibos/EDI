import { Component, input } from '@angular/core';

import { projectsOf } from './projects';
import { LandedTask, TaskLinks } from './sitrep';
import { LinkChips } from './link-chips';
import { stardate } from './stardate';

@Component({
  selector: 'app-landed-list',
  imports: [LinkChips],
  templateUrl: './landed-list.html',
})
export class LandedList {
  readonly tasks = input.required<LandedTask[]>();
  readonly total = input.required<number>();
  readonly zone = input.required<string>();
  readonly links = input<Record<string, TaskLinks>>({});

  protected readonly projectsOf = projectsOf;
  protected readonly stardate = stardate;
}
