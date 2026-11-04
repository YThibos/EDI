import { TestBed } from '@angular/core/testing';
import { Title } from '@angular/platform-browser';
import { Router, provideRouter } from '@angular/router';

import { Attention } from './attention';
import { DecisionCard, Sitrep } from './sitrep';
import { sitrepFixture } from './sitrep.fixture';

const BULB: DecisionCard = {
  number: 'D59',
  task: 'bulb-choice',
  projects: ['harness'],
  title: 'Which bulb?',
  context: '',
  options: [{ key: 'A', text: 'The warm one' }],
  recommended: { key: 'A', why: 'it is enough' },
  raisedAt: '2026-10-15T06:10:00Z',
};

class StubNotification {
  static permission: NotificationPermission = 'granted';
  static shown: { title: string; body?: string }[] = [];
  static requests = 0;

  constructor(title: string, options?: NotificationOptions) {
    StubNotification.shown.push({ title, body: options?.body });
  }

  static requestPermission(): Promise<NotificationPermission> {
    StubNotification.requests++;
    return Promise.resolve('granted');
  }
}

describe('Attention', () => {
  let attention: Attention;
  let title: Title;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: '', title: 'SITREP · EDI', children: [] },
          { path: 'fleet', title: 'FLEET · EDI', children: [] },
        ]),
      ],
    });
    await TestBed.inject(Router).navigateByUrl('/');
    StubNotification.permission = 'granted';
    StubNotification.shown = [];
    StubNotification.requests = 0;
    vi.stubGlobal('Notification', StubNotification);
    localStorage.clear();
    attention = TestBed.inject(Attention);
    title = TestBed.inject(Title);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('counts what waits on the captain in the tab title, and drops the count once nothing waits', () => {
    attention.follow(sitrepFixture(), 2);
    const counted = title.getTitle();
    attention.follow(sitrepFixture({ decisions: [] }), 0);

    expect(counted).toBe('(2) SITREP · EDI');
    expect(title.getTitle()).toBe('SITREP · EDI');
  });

  it('counts onto the title of the view on screen', async () => {
    await TestBed.inject(Router).navigateByUrl('/fleet');
    attention.follow(sitrepFixture(), 1);

    expect(title.getTitle()).toBe('(1) FLEET · EDI');
  });

  it('announces a new decision or review once, never what the page already showed when it opened', () => {
    const opened = sitrepFixture();
    attention.follow(opened, 1);
    const withMore: Sitrep = sitrepFixture({
      decisions: [...opened.decisions, BULB],
      reviews: [{ task: 'paint-buoy', title: 'Paint the buoy', projects: [], url: 'https://github.com/example-owner/harness/pull/14' }],
    });
    attention.follow(withMore, 3);
    attention.follow(withMore, 3);

    expect(StubNotification.shown).toEqual([
      { title: 'EDI', body: 'I need your decision on D59: Which bulb?' },
      { title: 'EDI', body: 'I have Paint the buoy ready for your review.' },
    ]);
    expect(title.getTitle()).toBe('(3) SITREP · EDI');
  });

  it('shows nothing without the browser\'s permission and asks for it only once', () => {
    StubNotification.permission = 'default';
    attention.follow(sitrepFixture(), 1);
    attention.follow(sitrepFixture({ decisions: [BULB] }), 1);
    attention.askOnce();
    attention.askOnce();
    TestBed.resetTestingModule();
    TestBed.inject(Attention).askOnce();

    expect(StubNotification.shown).toEqual([]);
    expect(StubNotification.requests).toBe(1);
  });

  it('works without the browser offering notifications at all', () => {
    vi.stubGlobal('Notification', undefined);

    attention.follow(sitrepFixture(), 1);
    attention.follow(sitrepFixture({ decisions: [BULB] }), 1);
    attention.askOnce();

    expect(title.getTitle()).toBe('(1) SITREP · EDI');
  });
});
