import { Component, computed, input } from '@angular/core';

import { projectsOf } from './projects';
import { AnsweredDecision } from './sitrep';
import { shipTime } from './stardate';

@Component({
  selector: 'app-answered-card',
  templateUrl: './answered-card.html',
})
export class AnsweredCard {
  readonly answer = input.required<AnsweredDecision>();

  protected readonly projects = computed(() => projectsOf(this.answer().projects, this.answer().task));
  protected readonly given = computed(() => {
    const answer = this.answer();
    const option = answer.option ? `option ${answer.option}, ` : '';
    return `${option}in the ${answer.channel} at ${shipTime(answer.answeredAt)}`;
  });
}
