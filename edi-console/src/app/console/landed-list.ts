import { Component, input } from '@angular/core';

import { projectsOf } from './projects';
import { LandedTask } from './sitrep';
import { stardate } from './stardate';

@Component({
  selector: 'app-landed-list',
  templateUrl: './landed-list.html',
})
export class LandedList {
  readonly tasks = input.required<LandedTask[]>();
  readonly total = input.required<number>();

  protected readonly projectsOf = projectsOf;
  protected readonly stardate = stardate;
}
