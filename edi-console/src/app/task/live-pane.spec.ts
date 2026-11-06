import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EVENT_SOURCES } from '../console/fleet-stream';
import { TaskDetail, WorkerDetail } from '../console/task-detail';
import { LivePane } from './live-pane';

class StubEventSource {
  static opened: StubEventSource[] = [];
  readyState = 1;
  closed = false;
  onerror: (() => void) | null = null;
  private readonly listeners = new Map<string, (event: MessageEvent<string>) => void>();

  constructor(readonly url: string) {
    StubEventSource.opened.push(this);
  }

  addEventListener(name: string, listener: (event: MessageEvent<string>) => void): void {
    this.listeners.set(name, listener);
  }

  close(): void {
    this.closed = true;
    this.readyState = 2;
  }

  emit(name: string, data: string): void {
    this.listeners.get(name)?.(new MessageEvent(name, { data }));
  }
}

const WORKER: WorkerDetail = {
  worker: { id: 'fit-lamp#1', route: { model: 'claude-opus-5-5', effort: 'high' }, activity: 'working' },
  steers: [
    { number: 1, body: 'Use the brass one', createdAt: '2026-10-15T09:00:00Z', acknowledgedAt: '2026-10-15T09:01:00Z' },
    { number: 2, body: 'Keep the old bracket', createdAt: '2026-10-15T09:10:00Z', acknowledgedAt: null },
  ],
};

const DETAIL = {
  worked: { active: 'PT1H5M', wallClock: 'PT3H' },
  cost: { workers: { input: 1000, cacheRead: 200000, cacheWrite: 30000, output: 4000 }, pipeline: { input: 0, cacheRead: 0, cacheWrite: 0, output: 0 } },
} as unknown as TaskDetail;

describe('LivePane', () => {
  let fixture: ComponentFixture<LivePane>;
  let http: HttpTestingController;

  beforeEach(() => {
    StubEventSource.opened = [];
    vi.stubGlobal('EventSource', { CLOSED: 2 });
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: EVENT_SOURCES, useValue: (url: string) => new StubEventSource(url) }],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(LivePane);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('streams the worker\'s pane with its colours, read-only, beside its model, effort, tokens, active time and waiting steer', async () => {
    const live = await opened();

    StubEventSource.opened[0].emit('pane', JSON.stringify({ lines: ['\u001b[31mThe lamp is lit\u001b[0m', 'claude-opus-5-5 · 41% context'] }));
    await fixture.whenStable();

    expect(StubEventSource.opened.map((source) => source.url)).toEqual(['/api/tasks/fit-lamp/live']);
    expect(Array.from(live.querySelectorAll('.sr-live__line')).map((line) => line.textContent)).toEqual(['The lamp is lit', 'claude-opus-5-5 · 41% context']);
    expect((live.querySelector('.sr-live__line span') as HTMLElement).style.color).toBe('rgb(255, 100, 121)');
    expect(facts(live)).toEqual({
      Worker: 'fit-lamp#1 · working',
      Model: 'claude-opus-5-5',
      Effort: 'high',
      Tokens: '235,000',
      Active: '1h05m of 3h00m',
      Context: "on the pane's status line",
    });
    expect(live.querySelector('.sr-live__steer')?.textContent?.replace(/\s+/g, ' ').trim()).toBe('steer 2 waiting: Keep the old bracket');
    expect(live.querySelectorAll('input, textarea, button, form')).toHaveLength(0);
  });

  it('closes the stream when the tab closes, so no capture runs without it', async () => {
    await opened();

    fixture.destroy();

    expect(StubEventSource.opened[0].closed).toBe(true);
  });

  it('says why the pane ended and stops listening', async () => {
    const live = await opened();

    StubEventSource.opened[0].emit('ended', "The worker's tmux window @1 is no longer on the edi server.");
    await fixture.whenStable();

    expect(live.querySelector('.sr-empty')?.textContent).toBe("The worker's tmux window @1 is no longer on the edi server.");
    expect(StubEventSource.opened[0].closed).toBe(true);
  });

  async function opened(): Promise<HTMLElement> {
    fixture.componentRef.setInput('task', 'fit-lamp');
    fixture.componentRef.setInput('detail', DETAIL);
    await fixture.whenStable();
    http.expectOne('/api/tasks/fit-lamp/worker').flush(WORKER);
    await fixture.whenStable();
    return fixture.nativeElement;
  }
});

function facts(live: HTMLElement): Record<string, string> {
  return Object.fromEntries(Array.from(live.querySelectorAll('.sr-live__facts > div')).map((fact) =>
    [fact.querySelector('dt')?.textContent?.trim() ?? '', fact.querySelector('dd')?.textContent?.trim() ?? '']));
}
