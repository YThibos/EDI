import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DecisionDeck } from './decision-deck';
import { DecisionCard } from './sitrep';
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
    expect(Array.from(deck.querySelectorAll('.sr-ctx__v')).map((value) => value.textContent)).toContain('option B, in the console at 07:40 CEST');
    expect(deck.querySelector('.sr-call')?.classList).toContain('sr-call--last');
  });

  it('offers no next card when the deck holds one, so paging never shows that card again', async () => {
    await dealt(sitrepFixture({ decisions: [] }));
    const deck: HTMLElement = fixture.nativeElement;
    const next = deck.querySelector<HTMLButtonElement>('[aria-label="Next card"]')!;

    next.click();
    await fixture.whenStable();

    expect(deck.querySelector('.sr-stacknav__count')?.textContent).toBe('card 1 of 1 · 1 answered');
    expect(next.disabled).toBe(true);
    expect(deck.querySelector<HTMLButtonElement>('[aria-label="Previous card"]')!.disabled).toBe(true);
    expect(deck.querySelectorAll('.sr-decision')).toHaveLength(1);
  });

  it('shows the all-clear when nothing waits and nothing was answered today', async () => {
    await dealt(sitrepFixture({ decisions: [], answered: [] }));
    const deck: HTMLElement = fixture.nativeElement;

    expect(deck.querySelector('.sr-clear__title')?.textContent).toBe('Nothing needs your decision right now.');
    expect(deck.querySelector('.sr-stacknav')).toBeNull();
  });

  it('starts the next decision empty once the captain answered the one before it', async () => {
    const [lamp, horn] = twoDecisions();
    await dealt(sitrepFixture({ decisions: [lamp, horn], answered: [] }));
    await chooseB();
    await type('go');
    fixture.nativeElement.querySelector('.sr-btn').click();
    TestBed.inject(HttpTestingController).expectOne('/api/decisions/D58/answer').flush('Recorded the answer to D58 (option B).\n');

    await dealt(sitrepFixture({ decisions: [horn], answered: [] }));

    expectEmpty('D59');
  });

  it('starts the next decision empty when the captain moves on from a half-filled card', async () => {
    await dealt(sitrepFixture({ decisions: twoDecisions(), answered: [] }));
    await chooseB();
    await type('go');

    fixture.nativeElement.querySelector('[aria-label="Next card"]').click();
    await fixture.whenStable();

    expectEmpty('D59');
  });

  it('keeps what the captain is typing when the sitrep refreshes the same decision', async () => {
    const decisions = twoDecisions();
    await dealt(sitrepFixture({ decisions, answered: [] }));
    await chooseB();
    await type('go');

    await dealt(sitrepFixture({ decisions: decisions.map((decision) => ({ ...decision })), answered: [] }));

    expect(freeform().value).toBe('go');
    expect(checked()).toEqual(['B']);
  });

  function twoDecisions(): DecisionCard[] {
    const [lamp] = sitrepFixture().decisions;
    return [lamp, { ...lamp, number: 'D59', task: 'horn-choice', title: 'Which horn?' }];
  }

  async function chooseB(): Promise<void> {
    const options = fixture.nativeElement.querySelectorAll('.sr-opt input');
    options[1].click();
    await fixture.whenStable();
  }

  async function type(words: string): Promise<void> {
    freeform().value = words;
    freeform().dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  function expectEmpty(decision: string): void {
    expect(fixture.nativeElement.querySelector('.sr-decision__repo')?.textContent).toContain(decision);
    expect(checked()).toEqual([]);
    expect(freeform().value).toBe('');
    expect(fixture.nativeElement.querySelector('.sr-queued')).toBeNull();
    expect(fixture.nativeElement.querySelector('.sr-limit')).toBeNull();
  }

  function freeform(): HTMLInputElement {
    return fixture.nativeElement.querySelector('.sr-freeform');
  }

  function checked(): string[] {
    return Array.from<HTMLInputElement>(fixture.nativeElement.querySelectorAll('.sr-opt input'))
      .filter((option) => option.checked)
      .map((option) => option.value);
  }

  async function dealt(sitrep: ReturnType<typeof sitrepFixture>): Promise<void> {
    fixture.componentRef.setInput('cards', sitrep.decisions);
    fixture.componentRef.setInput('answered', sitrep.answered);
    fixture.componentRef.setInput('zone', sitrep.zone);
    await fixture.whenStable();
  }
});
