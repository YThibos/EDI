import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LinkChips } from './link-chips';
import { TaskLinks } from './sitrep';

const LAMP: TaskLinks = {
  key: 'SVC-7',
  tracker: 'https://tracker.example.org/browse/SVC-7',
  request: { url: 'https://forge.example.org/team/harness/-/merge_requests/13', number: 13, state: 'open', step: 'review' },
  branch: { name: 'feature/SVC-7', url: 'https://forge.example.org/team/harness/-/tree/feature/SVC-7' },
  board: 'http://127.0.0.1:4387/session/1a2b3c',
  report: '/api/tasks/fit-lamp/pages/report',
  decisions: ['D58'],
};

describe('LinkChips', () => {
  let fixture: ComponentFixture<LinkChips>;

  beforeEach(() => {
    fixture = TestBed.createComponent(LinkChips);
  });

  it('links the key to the tracker, the request with its state and step, the branch, the board and the report, and names the decisions', async () => {
    const chips = await shown(LAMP);

    expect(Array.from(chips.querySelectorAll('.sr-chip')).map((chip) => [chip.textContent?.trim(), chip.getAttribute('href')])).toEqual([
      ['SVC-7', LAMP.tracker],
      ['#13 · open · review', LAMP.request?.url],
      ['feature/SVC-7', LAMP.branch?.url],
      ['board', LAMP.board],
      ['report', LAMP.report],
      ['D58', null],
    ]);
  });

  it('shows a key the tracker cannot link as plain text and leaves out every link it lacks', async () => {
    const chips = await shown({ key: 'GRD-2', tracker: null, request: null, branch: { name: 'feature/GRD-2', url: null }, board: null, report: null,
      decisions: [] });

    expect(Array.from(chips.querySelectorAll('.sr-chip')).map((chip) => [chip.tagName.toLowerCase(), chip.textContent?.trim()])).toEqual([
      ['span', 'GRD-2'],
      ['span', 'feature/GRD-2'],
    ]);
  });

  it('draws nothing for a row without links', async () => {
    const chips = await shown(undefined);

    expect(chips.querySelector('.sr-chips')).toBeNull();
  });

  async function shown(links: TaskLinks | undefined): Promise<HTMLElement> {
    fixture.componentRef.setInput('links', links);
    await fixture.whenStable();
    return fixture.nativeElement;
  }
});
