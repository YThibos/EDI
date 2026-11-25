import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DecisionCardView } from './decision-card';
import { sitrepFixture } from './sitrep.fixture';

describe('DecisionCardView', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<DecisionCardView>;
  let card: HTMLElement;
  let settled: number;

  beforeEach(async () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(DecisionCardView);
    fixture.componentRef.setInput('card', sitrepFixture().decisions[0]);
    settled = 0;
    fixture.componentInstance.settled.subscribe(() => settled++);
    await fixture.whenStable();
    card = fixture.nativeElement;
  });

  afterEach(() => http.verify());

  it('marks the recommended option and waits for a choice before it answers', () => {
    const options = Array.from(card.querySelectorAll('.sr-opt'));

    expect(options.map((option) => option.querySelector('.sr-opt__label')?.textContent?.trim())).toEqual([
      'AThe simple way',
      'BThe thorough way',
      'Reconcile',
    ]);
    expect(options.map((option) => option.querySelector('input')?.getAttribute('aria-label'))).toEqual([
      'A: The simple way (recommended)',
      'B: The thorough way',
      'Reconcile: re-check the latest state instead of answering',
    ]);
    expect(options[0].querySelector('.sr-opt__rec')?.textContent).toBe('rec');
    expect(options[0].querySelector('.sr-opt__hint')?.textContent).toBe('it is enough');
    expect(button().disabled).toBe(true);
  });

  it('answers with the option the captain chose, in the words the card showed', async () => {
    await choose(0);
    submit();

    const answer = http.expectOne('/api/decisions/D58/answer');
    expect(answer.request.headers.get('X-EDI-Console')).toBe('1');
    expect(answer.request.body).toEqual({ option: 'A', text: 'A: The simple way' });
    answer.flush('Recorded the answer to D58 (option A); lamp-choice is done.\n');
    await fixture.whenStable();

    expect(settled).toBe(1);
    expect(card.querySelector('.sr-queued')?.textContent?.trim()).toBe('answer stored');
    expect(card.querySelector('.sr-limit')).toBeNull();
  });

  it('records the captain’s own words verbatim', async () => {
    await choose(1);
    await type('B, but keep "one" oil lamp');
    submit();

    expect(http.expectOne('/api/decisions/D58/answer').request.body).toEqual({ option: 'B', text: 'B, but keep "one" oil lamp' });
  });

  it('asks for a re-check instead of answering when the captain chooses Reconcile', async () => {
    await choose(2);
    await type('the lamp came in today');
    submit();

    const recheck = http.expectOne('/api/decisions/D58/recheck');
    expect(recheck.request.body).toEqual({ note: 'the lamp came in today' });
    recheck.flush('Asked EDI to re-check D58 against reality; it stays open until it is answered.\n');
    await fixture.whenStable();
    expect(card.querySelector('.sr-queued')?.textContent?.trim()).toBe('re-check asked');
  });

  it('shows on the card why the core did not store the answer, and keeps the card open', async () => {
    await choose(0);
    submit();

    http.expectOne('/api/decisions/D58/answer').flush('D58 is already answered: D58 B\n', { status: 409, statusText: 'Conflict' });
    await fixture.whenStable();

    expect(card.querySelector('.sr-limit')?.textContent).toBe('Not stored. D58 is already answered: D58 B');
    expect(card.querySelector('.sr-queued')).toBeNull();
    expect(card.querySelector('form')).not.toBeNull();
    expect(button().disabled).toBe(false);
    expect(settled).toBe(0);
  });

  it('tells an expired or replaced console session how to sign in again, and keeps the card open', async () => {
    await choose(0);
    submit();

    http
      .expectOne('/api/decisions/D58/answer')
      .flush('This console session has ended, or a newer sign-in replaced it: run edi console in the primary session to sign in again.\n', {
        status: 401,
        statusText: 'Unauthorized',
      });
    await fixture.whenStable();

    expect(card.querySelector('.sr-limit')?.textContent).toBe(
      'Not stored. Your console session has ended, or a newer sign-in replaced it: run edi console in the primary session to sign in again.',
    );
    expect(card.querySelector('.sr-queued')).toBeNull();
    expect(button().disabled).toBe(false);
    expect(settled).toBe(0);
  });

  it('says the answer was not stored when the core does not answer at all', async () => {
    await choose(0);
    submit();

    http.expectOne('/api/decisions/D58/answer').error(new ProgressEvent('error'));
    await fixture.whenStable();

    expect(card.querySelector('.sr-limit')?.textContent).toBe('Not stored. I did not answer; my core may be restarting, so try again in a moment.');
    expect(settled).toBe(0);
  });

  async function choose(index: number): Promise<void> {
    const radio = card.querySelectorAll<HTMLInputElement>('.sr-opt input')[index];
    radio.checked = true;
    radio.dispatchEvent(new Event('change'));
    await fixture.whenStable();
  }

  async function type(words: string): Promise<void> {
    const box = card.querySelector<HTMLInputElement>('.sr-freeform')!;
    box.value = words;
    box.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  function submit(): void {
    card.querySelector('form')!.dispatchEvent(new Event('submit'));
  }

  function button(): HTMLButtonElement {
    return card.querySelector<HTMLButtonElement>('.sr-btn')!;
  }
});
