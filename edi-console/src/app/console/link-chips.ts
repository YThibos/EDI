import { Component, input } from '@angular/core';

import { TaskLinks } from './sitrep';

@Component({
  selector: 'app-link-chips',
  templateUrl: './link-chips.html',
  styleUrl: './link-chips.css',
})
export class LinkChips {
  readonly links = input<TaskLinks | undefined>(undefined);
  readonly showsKey = input(true);

  protected requestWords(request: NonNullable<TaskLinks['request']>): string {
    return [`#${request.number}`, request.state, request.step].filter((word) => !!word).join(' · ');
  }
}
