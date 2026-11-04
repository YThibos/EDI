import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { projectsOf } from './projects';
import { OngoingTask, TaskLinks } from './sitrep';
import { LinkChips } from './link-chips';
import { toneOf } from './tone';

@Component({
  selector: 'app-ongoing-list',
  imports: [RouterLink, LinkChips],
  templateUrl: './ongoing-list.html',
})
export class OngoingList {
  readonly tasks = input.required<OngoingTask[]>();
  readonly links = input<Record<string, TaskLinks>>({});

  protected readonly projectsOf = projectsOf;
  protected readonly toneOf = toneOf;
}
