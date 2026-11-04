import { Component, computed, input, linkedSignal, output } from '@angular/core';

import { AnsweredCard } from './answered-card';
import { DecisionCardView } from './decision-card';
import { AnsweredDecision, DecisionCard, TaskLinks } from './sitrep';

type DeckCard = { open: DecisionCard; answered?: never } | { answered: AnsweredDecision; open?: never };

@Component({
  selector: 'app-decision-deck',
  imports: [DecisionCardView, AnsweredCard],
  templateUrl: './decision-deck.html',
  styleUrl: './decision-deck.css',
})
export class DecisionDeck {
  readonly cards = input.required<DecisionCard[]>();
  readonly answered = input.required<AnsweredDecision[]>();
  readonly zone = input.required<string>();
  readonly links = input<Record<string, TaskLinks>>({});
  readonly settled = output<void>();

  protected readonly deck = computed<DeckCard[]>(() => [
    ...this.cards().map((open) => ({ open })),
    ...this.answered().map((answered) => ({ answered })),
  ]);
  protected readonly active = linkedSignal<DeckCard[], number>({
    source: this.deck,
    computation: (deck, previous) => Math.min(previous?.value ?? 0, Math.max(deck.length - 1, 0)),
  });
  protected readonly current = computed(() => this.deck()[this.active()]);
  protected readonly count = computed(() => {
    const answered = this.answered().length ? ` · ${this.answered().length} answered` : '';
    return `card ${this.active() + 1} of ${this.deck().length}${answered}`;
  });

  protected show(index: number): void {
    this.active.set(Math.max(0, Math.min(this.deck().length - 1, index)));
  }
}
