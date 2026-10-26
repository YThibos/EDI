import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ReadyForReview, WaitingBoard } from './sitrep';
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

  async function listed(boards: WaitingBoard[], reviews: ReadyForReview[]): Promise<HTMLElement> {
    fixture.componentRef.setInput('boards', boards);
    fixture.componentRef.setInput('reviews', reviews);
    await fixture.whenStable();
    return fixture.nativeElement;
  }
});

function texts(list: HTMLElement, selector: string): string[] {
  return Array.from(list.querySelectorAll(selector)).map((element) => element.textContent?.trim() ?? '');
}
