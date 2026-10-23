import { ComponentFixture, TestBed } from '@angular/core/testing';

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
};

const UNKEYED_HORN: OngoingTask = { ...KEYED_LAMP, task: 'tune-horn', key: null, title: 'Tune the horn' };

describe('OngoingList', () => {
  let fixture: ComponentFixture<OngoingList>;

  beforeEach(() => {
    fixture = TestBed.createComponent(OngoingList);
  });

  it('leads a keyed task with its work key and shows an unkeyed task by its title alone', async () => {
    const list = await listed([KEYED_LAMP, UNKEYED_HORN]);

    expect(texts(list, '.sr-row__title')).toEqual(['DOCK-58 Fit the lamp', 'Tune the horn']);
    expect(texts(list, '.sr-row__key')).toEqual(['DOCK-58']);
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
