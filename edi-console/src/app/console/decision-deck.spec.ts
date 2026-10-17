import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DecisionDeck } from './decision-deck';
import { sitrepFixture } from './sitrep.fixture';

describe('DecisionDeck', () => {
  let fixture: ComponentFixture<DecisionDeck>;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    fixture = TestBed.createComponent(DecisionDeck);
  });

  it('deals the open decisions first and keeps today’s answers behind them, answered', async () => {
    await dealt(sitrepFixture());
    const deck: HTMLElement = fixture.nativeElement;

    expect(deck.querySelector('.sr-stacknav__count')?.textContent).toBe('card 1 of 2 · 1 answered');
    expect(deck.querySelector('.sr-call')?.classList).toContain('sr-call--penult');

    deck.querySelector<HTMLButtonElement>('[aria-label="Next card"]')!.click();
    await fixture.whenStable();

    expect(deck.querySelector('.sr-badge')?.textContent?.trim()).toBe('answered');
    expect(deck.querySelector('.sr-ctx__v')?.textContent).toBe('B, and tune it lower');
    expect(Array.from(deck.querySelectorAll('.sr-ctx__v')).map((value) => value.textContent)).toContain('option B, in the console at 05:40Z');
    expect(deck.querySelector('.sr-call')?.classList).toContain('sr-call--last');
  });

  it('shows the all-clear when nothing waits and nothing was answered today', async () => {
    await dealt(sitrepFixture({ decisions: [], answered: [] }));
    const deck: HTMLElement = fixture.nativeElement;

    expect(deck.querySelector('.sr-clear__title')?.textContent).toBe('Nothing needs your decision right now.');
    expect(deck.querySelector('.sr-stacknav')).toBeNull();
  });

  async function dealt(sitrep: ReturnType<typeof sitrepFixture>): Promise<void> {
    fixture.componentRef.setInput('cards', sitrep.decisions);
    fixture.componentRef.setInput('answered', sitrep.answered);
    await fixture.whenStable();
  }
});
