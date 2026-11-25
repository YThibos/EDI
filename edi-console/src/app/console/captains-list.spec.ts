import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { CaptainsList } from './captains-list';
import { entriesOf } from './captains-list-entries';
import { DecisionCard, Sitrep } from './sitrep';
import { sitrepFixture } from './sitrep.fixture';

const LAMP = sitrepFixture().decisions[0];
const STOPPER: DecisionCard = { ...LAMP, number: 'D60', task: 'fit-socket', title: 'Which socket?', stops: 'EDI-31 Fit the lamp', raisedAt: '2026-10-15T04:10:00Z' };
const URGENT: DecisionCard = { ...LAMP, number: 'D61', task: 'storm-plan', title: 'Reef the sails?', urgent: true };
const BOARD = {
  call: 'board:3',
  task: 'lamp-research',
  title: 'Research the lamp',
  projects: ['harness'],
  questions: 2,
  link: 'http://127.0.0.1:4387/session/1a2b3c',
  decisions: [],
};
const REVIEW_WITH_STEPS = {
  task: 'fit-lamp',
  title: 'Fit the lamp',
  projects: ['harness'],
  url: 'https://github.com/example-owner/harness/pull/13',
  afterLanding: { steps: ['Run edi up.', 'Restart the desktop core.'], landingOrder: 'Merges after #12.' },
};
const REVIEW_WITHOUT_STEPS = {
  task: 'paint-buoy',
  title: 'Paint the buoy',
  projects: ['harness'],
  url: 'https://github.com/example-owner/harness/pull/14',
  afterLanding: { steps: [], landingOrder: null },
};

describe('CaptainsList', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<CaptainsList>;
  let settled: number;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(CaptainsList);
    settled = 0;
    fixture.componentInstance.settled.subscribe(() => settled++);
  });

  afterEach(() => http.verify());

  it('lists what stops a worker first and says which work it stops, then the other decisions, the boards, the reviews and what is dealt with', async () => {
    const list = await listed(everything());

    expect(texts(list, '.sr-entry__title')).toEqual([
      'D60 Which socket?',
      'D58 Which lamp?',
      'D61 Reef the sails?',
      'Board for Research the lamp',
      'Fit the lamp',
      'Paint the buoy',
      'D57 Which horn?',
    ]);
    expect(texts(list, '.sr-entry__stops')).toEqual(['stops EDI-31 Fit the lamp']);
    expect(texts(list, '.sr-entry__sub')[0]).toBe('stops EDI-31 Fit the lamp · waiting 2 hours 7 minutes · harness');
    expect(texts(list, '.sr-entry__line > .sr-badge')).toEqual(['decision', 'decision', 'urgent', 'board', 'review', 'review', 'answered']);
  });

  it('opens the first entry that stops a worker when the page opens, and only that one', async () => {
    const list = await listed(everything());

    expect(openEntries(list)).toEqual(['D60 Which socket?']);
    expect(list.querySelectorAll('app-decision-card')).toHaveLength(1);
    expect(list.querySelector('.sr-opt__rec')?.textContent).toBe('rec');
  });

  it('opens nothing by itself when nothing stops a worker', async () => {
    const list = await listed(sitrepFixture());

    expect(openEntries(list)).toEqual([]);
  });

  it('keeps one entry open at a time, and closes every entry again', async () => {
    const list = await listed(everything());

    await toggle(list, 'D58');
    expect(openEntries(list)).toEqual(['D58 Which lamp?']);

    await toggle(list, 'D58');
    expect(openEntries(list)).toEqual([]);

    await toggle(list, 'D57');
    expect(openEntries(list)).toEqual(['D57 Which horn?']);
    expect(text(list, '.sr-entry__body .sr-ctx__v')).toBe('B, and tune it lower');
  });

  it('opens a review to the steps that remain after the merge and its merge order, and offers nothing to open on a review without them', async () => {
    const list = await listed(everything());

    expect(toggleOf(list, 'review:paint-buoy')).toBeNull();
    expect(toggleOf(list, 'board:3')).toBeNull();

    await toggle(list, 'review:fit-lamp');

    expect(texts(list, '.sr-entry__steps li')).toEqual(['Run edi up.', 'Restart the desktop core.']);
    expect(text(list, '.sr-entry__order')).toBe('Merges after #12.');
  });

  it('names the branch a stacked review targets, since merging it does not land the work on the base', async () => {
    const list = await listed(sitrepFixture({ decisions: [], reviews: [{ ...REVIEW_WITHOUT_STEPS, stackedOn: 'slice/buoys' }] }));

    expect(texts(list, '.sr-entry__sub')[0]).toBe('ready for your review · harness · targets slice/buoys, not the base: merging it does not land the work');
  });

  it('lets the captain tick only what is dealt with, and tells the page once the tick is stored', async () => {
    const list = await listed(everything());
    const ticks = Array.from(list.querySelectorAll<HTMLInputElement>('.sr-entry__tick'));

    expect(ticks.map((tick) => tick.disabled)).toEqual([true, true, true, true, true, true, false]);

    ticks[6].click();
    await fixture.whenStable();
    const tick = http.expectOne('/api/calls/D57/tick');
    expect(tick.request.method).toBe('POST');
    expect(tick.request.headers.get('X-EDI-Console')).toBe('1');
    tick.flush('Ticked D57 off the captain\'s list.\n');

    expect(settled).toBe(1);
  });

  it('says why a tick did not land', async () => {
    const list = await listed(everything());

    list.querySelectorAll<HTMLInputElement>('.sr-entry__tick')[6].click();
    await fixture.whenStable();
    http.expectOne('/api/calls/D57/tick').flush('D57 still waits on the captain.\n', { status: 409, statusText: 'Conflict' });
    await fixture.whenStable();

    expect(text(list, '.sr-list__refusal')).toBe('D57 still waits on the captain.');
    expect(settled).toBe(0);
  });

  it('lists an answered round, an ended board and a merged request as dealt with until the captain ticks them', async () => {
    const list = await listed(
      sitrepFixture({
        decisions: [],
        dealtWith: {
          decisions: [],
          boards: [
            { call: 'board:2', task: 'bell-research', title: 'Research the bell', projects: ['harness'], link: null, state: 'round answered' },
            { call: 'board:3', task: 'lamp-research', title: 'Research the lamp', projects: ['harness'], link: null, state: 'the board ended' },
          ],
          reviews: [{ ...REVIEW_WITH_STEPS, call: 'review:fit-lamp', state: 'merged' }],
        },
      }),
    );

    expect(texts(list, '.sr-entry__sub')).toEqual(['round answered · harness', 'the board ended · harness', 'merged · harness']);
    expect(texts(list, '.sr-entry__line > .sr-badge')).toEqual(['answered', 'ended', 'merged']);
    expect(list.querySelectorAll('.sr-entry--dealt')).toHaveLength(3);

    await toggle(list, 'review:fit-lamp');

    expect(texts(list, '.sr-entry__steps li')).toEqual(['Run edi up.', 'Restart the desktop core.']);
  });

  function everything(): Sitrep {
    return sitrepFixture({
      decisions: [LAMP, URGENT, STOPPER],
      boards: [BOARD],
      reviews: [REVIEW_WITH_STEPS, REVIEW_WITHOUT_STEPS],
    });
  }

  async function listed(sitrep: Sitrep): Promise<HTMLElement> {
    fixture.componentRef.setInput('entries', entriesOf(sitrep));
    fixture.componentRef.setInput('zone', sitrep.zone);
    fixture.componentRef.setInput('generated', sitrep.generated);
    await fixture.whenStable();
    return fixture.nativeElement;
  }

  async function toggle(list: HTMLElement, key: string): Promise<void> {
    toggleOf(list, key)!.click();
    await fixture.whenStable();
  }
});

function toggleOf(list: HTMLElement, key: string): HTMLButtonElement | null {
  return list.querySelector<HTMLButtonElement>(`.sr-entry__toggle[aria-label$=" ${key}"]`);
}

function openEntries(list: HTMLElement): string[] {
  return texts(list, '.sr-entry--open .sr-entry__title');
}

function text(list: HTMLElement, selector: string): string {
  return list.querySelector(selector)?.textContent?.trim() ?? '';
}

function texts(list: HTMLElement, selector: string): string[] {
  return Array.from(list.querySelectorAll(selector)).map((element) => element.textContent?.replace(/\s+/g, ' ').trim() ?? '');
}
