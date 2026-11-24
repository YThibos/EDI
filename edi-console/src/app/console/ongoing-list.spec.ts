import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { OngoingList } from './ongoing-list';
import { OngoingTask } from './sitrep';

const KEYED_LAMP: OngoingTask = {
  task: 'fit-lamp',
  key: 'DOCK-58',
  title: 'Fit the lamp',
  kind: 'ship',
  projects: ['harness'],
  state: 'working',
  doing: 'pipeline review',
  draft: null,
  comingBack: null,
  limitResetsAt: null,
};

const UNKEYED_HORN: OngoingTask = { ...KEYED_LAMP, task: 'tune-horn', key: null, title: 'Tune the horn' };

describe('OngoingList', () => {
  let fixture: ComponentFixture<OngoingList>;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    fixture = TestBed.createComponent(OngoingList);
  });

  it('leads a keyed task with its work key and shows an unkeyed task by its title alone', async () => {
    const list = await listed([KEYED_LAMP, UNKEYED_HORN]);

    expect(texts(list, '.sr-row__title')).toEqual(['DOCK-58 Fit the lamp', 'Tune the horn']);
    expect(texts(list, '.sr-row__key')).toEqual(['DOCK-58']);
  });

  it('marks a request still changing as coming back to the captain when it is ready', async () => {
    const list = await listed([{ ...KEYED_LAMP, state: 'working', comingBack: 'pipeline review; steer 2 not acknowledged' }]);

    expect(texts(list, '.sr-row__back')).toEqual(['changing, back to you when it is ready: pipeline review; steer 2 not acknowledged']);
  });

  it('marks a worker stopped on the usage limit as waiting for the reset at its local time', async () => {
    fixture.componentRef.setInput('zone', 'Europe/Brussels');
    const list = await listed([{ ...KEYED_LAMP, state: 'usage limit', doing: 'stopped on the session limit', limitResetsAt: '2026-10-15T16:00:00Z' }]);

    expect(texts(list, '.sr-row__sub')[0]).toBe('stopped on the session limit until 18:00 CEST · ship · harness');
    expect(texts(list, '.sr-badge')).toEqual(['usage limit']);
  });

  it('shows the question a worker asks of EDI under its task, with how long it has waited', async () => {
    fixture.componentRef.setInput('generated', '2026-10-15T06:20:00Z');
    fixture.componentRef.setInput('workerQuestions', [
      { task: 'fit-lamp', title: 'Fit the lamp', projects: ['harness'], question: 'Which socket?', askedAt: '2026-10-15T06:00:00Z' },
    ]);
    const list = await listed([KEYED_LAMP, UNKEYED_HORN]);

    expect(texts(list, '.sr-row__asked')).toEqual(['waiting on me for 20 minutes: Which socket?']);
  });

  async function listed(tasks: OngoingTask[]): Promise<HTMLElement> {
    fixture.componentRef.setInput('tasks', tasks);
    await fixture.whenStable();
    return fixture.nativeElement;
  }
});

function texts(list: HTMLElement, selector: string): string[] {
  return Array.from(list.querySelectorAll(selector)).map((element) => element.textContent?.trim() ?? '');
}
