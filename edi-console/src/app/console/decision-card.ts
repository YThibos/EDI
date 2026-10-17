import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, output, signal } from '@angular/core';
import { Observable } from 'rxjs';

import { refusalOf } from './answer-sentence';
import { ConsoleApi } from './console-api';
import { projectsOf } from './projects';
import { DecisionCard, GivenAnswer } from './sitrep';

const RECONCILE = 'reconcile';

@Component({
  selector: 'app-decision-card',
  templateUrl: './decision-card.html',
  styleUrl: './decision-card.css',
})
export class DecisionCardView {
  readonly card = input.required<DecisionCard>();
  readonly settled = output<void>();

  private readonly api = inject(ConsoleApi);

  protected readonly reconcile = RECONCILE;
  protected readonly choice = signal<string | null>(null);
  protected readonly words = signal('');
  protected readonly sending = signal(false);
  protected readonly refusal = signal<string | null>(null);
  protected readonly recheckAsked = signal(false);
  protected readonly projects = computed(() => projectsOf(this.card().projects, this.card().task));
  protected readonly canAnswer = computed(() => !this.sending() && (this.choice() !== null || this.words().trim().length > 0));

  protected answer(event: Event): void {
    event.preventDefault();
    if (!this.canAnswer()) {
      return;
    }
    this.refusal.set(null);
    this.sending.set(true);
    const askingForARecheck = this.choice() === RECONCILE;
    this.sent(askingForARecheck).subscribe({
      next: () => this.landed(askingForARecheck),
      error: (refused: HttpErrorResponse) => this.refused(refused),
    });
  }

  private sent(askingForARecheck: boolean): Observable<string> {
    const decision = this.card().number;
    return askingForARecheck ? this.api.askForARecheck(decision, this.words().trim()) : this.api.answer(decision, this.givenAnswer());
  }

  private landed(askingForARecheck: boolean): void {
    this.sending.set(false);
    this.recheckAsked.set(askingForARecheck);
    this.settled.emit();
  }

  private refused(response: HttpErrorResponse): void {
    this.sending.set(false);
    this.refusal.set(refusalOf(response));
  }

  private givenAnswer(): GivenAnswer {
    const words = this.words().trim();
    const option = this.card().options.find((offered) => offered.key === this.choice());
    return { option: option?.key ?? null, text: words || `${option?.key}: ${option?.text}` };
  }
}
