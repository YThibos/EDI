import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, input, output, signal } from '@angular/core';
import { fromEvent } from 'rxjs';

import { refusalOf } from './answer-sentence';
import { ConsoleApi } from './console-api';
import { Levels, PersonalityShown, PresetShown, SCALES, Scale, sameLevels } from './personality';
import { News, railChanges, sampleMessage } from './sample-message';

@Component({
  selector: 'app-personality-desk',
  templateUrl: './personality-desk.html',
  styleUrl: './personality-desk.css',
})
export class PersonalityDesk {
  readonly demoHolds = input(false);
  readonly closed = output<void>();

  private readonly api = inject(ConsoleApi);

  protected readonly scales = SCALES;
  protected readonly kept = signal<PersonalityShown | null>(null);
  protected readonly draft = signal<Levels | null>(null);
  protected readonly news = signal<News>('good');
  protected readonly sending = signal(false);
  protected readonly justKept = signal(false);
  protected readonly refusal = signal<string | null>(null);
  protected readonly changed = computed(() => {
    const kept = this.kept();
    const draft = this.draft();
    return !!kept && !!draft && !sameLevels(kept.levels, draft);
  });
  protected readonly canKeep = computed(() => !this.sending() && (this.changed() || this.kept()?.chosen === false));
  protected readonly railed = computed(() => {
    const draft = this.draft();
    return draft && this.news() === 'bad' ? railChanges(draft) : [];
  });
  protected readonly sample = computed(() => {
    const kept = this.kept();
    const draft = this.draft();
    return kept && draft ? sampleMessage(draft, this.news(), kept.address) : '';
  });
  protected readonly railWords = computed(() =>
    this.railed().length ? `Guard rails for bad news: ${this.railed().map((change) => `${change.scale} ${change.from} → ${change.to}`).join(', ')}.` : '',
  );

  constructor() {
    this.read();
    const focus = fromEvent(window, 'focus').subscribe(() => this.readUnlessChanged());
    inject(DestroyRef).onDestroy(() => focus.unsubscribe());
  }

  protected isChosen(preset: PresetShown): boolean {
    const draft = this.draft();
    return !!draft && sameLevels(preset.levels, draft);
  }

  protected isRailed(scale: Scale): boolean {
    return this.railed().some((change) => change.scale === scale);
  }

  protected choose(preset: PresetShown): void {
    this.draft.set({ ...preset.levels });
    this.justKept.set(false);
  }

  protected slide(scale: Scale, level: string): void {
    this.draft.update((draft) => draft && { ...draft, [scale]: Number(level) });
    this.justKept.set(false);
  }

  protected keep(event: Event): void {
    event.preventDefault();
    const draft = this.draft();
    if (!draft || !this.canKeep()) {
      return;
    }
    this.refusal.set(null);
    this.sending.set(true);
    this.api.keepPersonality(draft).subscribe({
      next: (personality) => this.landed(personality),
      error: (refused: HttpErrorResponse) => this.refused(refused),
    });
  }

  protected undo(): void {
    const kept = this.kept();
    if (kept) {
      this.draft.set({ ...kept.levels });
    }
  }

  private read(): void {
    this.api.personality().subscribe({
      next: (personality) => this.shown(personality),
      error: (refused: HttpErrorResponse) => this.refusal.set(refusalOf(refused)),
    });
  }

  private readUnlessChanged(): void {
    if (!this.changed() && !this.sending()) {
      this.read();
    }
  }

  private shown(personality: PersonalityShown): void {
    this.kept.set(personality);
    this.draft.set({ ...personality.levels });
  }

  private landed(personality: PersonalityShown): void {
    this.sending.set(false);
    this.shown(personality);
    this.justKept.set(true);
  }

  private refused(response: HttpErrorResponse): void {
    this.sending.set(false);
    this.refusal.set(refusalOf(response));
  }
}
