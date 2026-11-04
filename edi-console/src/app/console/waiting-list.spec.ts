import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ChangingRequest, ReadyForReview, WaitingBoard, WorkerQuestion } from './sitrep';
import { WaitingList } from './waiting-list';

const LAMP_BOARD: WaitingBoard = {
  task: 'lamp-research',
  title: 'Research the lamp',
  projects: ['harness'],
  questions: 3,
  link: 'http://127.0.0.1:4387/session/1a2b3c',
  decisions: [],
};

const BUOY_REVIEW: ReadyForReview = {
  task: 'paint-buoy',
  title: 'Paint the buoy',
  projects: [],
  url: 'https://github.com/example-owner/harness/pull/14',
};

const LAMP_CHANGING: ChangingRequest = {
  task: 'fit-lamp',
  title: 'Fit the lamp',
  projects: ['harness'],
  url: 'https://github.com/example-owner/harness/pull/13',
  why: 'pipeline review; steer 2 not acknowledged',
};

const LAMP_QUESTION: WorkerQuestion = {
  task: 'fit-lamp',
  title: 'Fit the lamp',
  projects: ['harness'],
  question: 'Which socket does the lamp take?',
  askedAt: '2026-10-15T05:12:00Z',
};

describe('WaitingList', () => {
  let fixture: ComponentFixture<WaitingList>;

  beforeEach(() => {
    fixture = TestBed.createComponent(WaitingList);
  });

  it('shows a board with its questions and link, and a review with its full address', async () => {
    const list = await listed([LAMP_BOARD], [BUOY_REVIEW]);

    expect(texts(list, '.sr-row__title')).toEqual(['Board for Research the lamp', 'Paint the buoy']);
    expect(texts(list, '.sr-row__sub')).toEqual(['3 questions waiting · harness', 'ready for your review · paint-buoy']);
    expect(Array.from(list.querySelectorAll<HTMLAnchorElement>('.sr-waiting__link')).map((link) => [link.textContent, link.href])).toEqual([
      [LAMP_BOARD.link, LAMP_BOARD.link],
      [BUOY_REVIEW.url, BUOY_REVIEW.url],
    ]);
    expect(texts(list, '.sr-badge')).toEqual(['board', 'review']);
  });

  it('lists each decision the board answers as one line under it, with its number and title only', async () => {
    const list = await listed([{ ...LAMP_BOARD, decisions: [{ number: 'D58', title: 'Which lamp?' }, { number: 'D60', title: 'Which bulb?' }] }], []);

    expect(texts(list, '.sr-waiting__decision .sr-row__key')).toEqual(['D58', 'D60']);
    expect(texts(list, '.sr-waiting__decision .sr-waiting__title')).toEqual(['Which lamp?', 'Which bulb?']);
  });

  it('counts a single question in the singular', async () => {
    const list = await listed([{ ...LAMP_BOARD, questions: 1 }], []);

    expect(texts(list, '.sr-row__sub')).toEqual(['1 question waiting · harness']);
  });

  it('names no question count for a board that asks none of its own and only answers decisions', async () => {
    const list = await listed([{ ...LAMP_BOARD, questions: 0, decisions: [{ number: 'D58', title: 'Which lamp?' }] }], []);

    expect(texts(list, '.sr-row__sub')).toEqual(['harness']);
  });

  it('shows a request still changing with a danger badge and why, in place of the review badge', async () => {
    const list = await listed([], [], [LAMP_CHANGING]);

    expect(texts(list, '.sr-row__title')).toEqual(['Fit the lamp']);
    expect(texts(list, '.sr-row__sub')).toEqual(['changing, hold the merge: pipeline review; steer 2 not acknowledged · harness']);
    expect(texts(list, '.sr-waiting__link')).toEqual([LAMP_CHANGING.url]);
    expect(texts(list, '.sr-badge')).toEqual(['changing']);
    expect(list.querySelector('.sr-badge')?.classList).toContain('sr-badge--danger');
  });

  it('shows a worker question as waiting on me, with the question and how long it has waited', async () => {
    fixture.componentRef.setInput('workerQuestions', [LAMP_QUESTION]);
    fixture.componentRef.setInput('generated', '2026-10-15T06:17:35Z');
    const list = await listed([], []);

    expect(texts(list, '.sr-row__title')).toEqual(['Fit the lamp']);
    expect(texts(list, '.sr-row__sub')).toEqual(['waiting on me for 1 hour 5 minutes: Which socket does the lamp take? · harness']);
    expect(texts(list, '.sr-badge')).toEqual(['with me']);
    expect(list.querySelector('.sr-badge')?.classList).toContain('sr-badge--info');
  });

  it('counts a question asked under an hour ago in minutes', async () => {
    fixture.componentRef.setInput('workerQuestions', [{ ...LAMP_QUESTION, askedAt: '2026-10-15T06:16:00Z' }]);
    fixture.componentRef.setInput('generated', '2026-10-15T06:17:35Z');
    const list = await listed([], []);

    expect(texts(list, '.sr-row__sub')).toEqual(['waiting on me for 1 minute: Which socket does the lamp take? · harness']);
  });

  async function listed(boards: WaitingBoard[], reviews: ReadyForReview[], changing: ChangingRequest[] = []): Promise<HTMLElement> {
    fixture.componentRef.setInput('boards', boards);
    fixture.componentRef.setInput('reviews', reviews);
    fixture.componentRef.setInput('changing', changing);
    await fixture.whenStable();
    return fixture.nativeElement;
  }
});

function texts(list: HTMLElement, selector: string): string[] {
  return Array.from(list.querySelectorAll(selector)).map((element) => element.textContent?.trim() ?? '');
}
