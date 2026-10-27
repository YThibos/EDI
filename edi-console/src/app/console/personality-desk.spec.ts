import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PersonalityDesk } from './personality-desk';
import { BALANCED, PLAIN, personalityFixture } from './personality.fixture';

describe('PersonalityDesk', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('starts on the personality EDI keeps, with the presets above all nine scales from 0 to 10', async () => {
    const desk = (await opened()).nativeElement as HTMLElement;

    expect(texts(desk, '.sr-slider__name')).toEqual([
      'Humour',
      'Formality',
      'Verbosity',
      'Warmth',
      'Directness',
      'Initiative',
      'Technical depth',
      'Enthusiasm',
      "Ship's flavour",
    ]);
    expect(sliders(desk).map((slider) => [slider.min, slider.max, slider.value])).toEqual(
      Object.values(BALANCED).map((level) => ['0', '10', String(level)]),
    );
    expect(texts(desk, '.sr-desk__levels .sr-chip')).toEqual(['Balanced', 'Plain', 'Demo']);
    expect(pressed(desk)).toEqual(['Balanced', 'Good news']);
    expect(text(desk, '.sr-bubble')).toMatch(/^EDI · good news\s*The stardate header \(EDI-31\) is ready for your review/);
    expect(button(desk, 'Keep').disabled).toBe(true);
    expect(button(desk, 'Undo').disabled).toBe(true);
  });

  it('keeps the levels on the sliders through the same core change as edi personality', async () => {
    const fixture = await opened();
    const desk = fixture.nativeElement as HTMLElement;

    slide(sliders(desk)[0], 9);
    await fixture.whenStable();
    expect(text(desk, '.sr-slider__level')).toBe('9');
    expect(pressed(desk)).toEqual(['Good news']);
    expect(text(desk, '.sr-bubble')).toContain('Kirk needed a whole crew for that');

    button(desk, 'Keep').click();
    const kept = http.expectOne({ method: 'POST', url: '/api/personality' });
    expect(kept.request.headers.get('X-EDI-Console')).toBe('1');
    expect(kept.request.body).toEqual({
      change: ['humour', '9', 'formality', '5', 'verbosity', '5', 'warmth', '4', 'directness', '6', 'initiative', '5', 'depth', '5', 'enthusiasm', '3', 'flavour', '5'],
    });
    kept.flush(personalityFixture({ levels: { ...BALANCED, humour: 9 } }));
    await fixture.whenStable();

    expect(text(desk, '.sr-queued')).toBe('kept');
    expect(button(desk, 'Keep').disabled).toBe(true);
  });

  it('sets every slider to a preset and takes it back to what EDI keeps on undo', async () => {
    const fixture = await opened();
    const desk = fixture.nativeElement as HTMLElement;

    button(desk, 'Plain').click();
    await fixture.whenStable();
    expect(sliders(desk).map((slider) => Number(slider.value))).toEqual(Object.values(PLAIN));
    expect(pressed(desk)).toEqual(['Plain', 'Good news']);

    button(desk, 'Undo').click();
    await fixture.whenStable();
    expect(sliders(desk).map((slider) => Number(slider.value))).toEqual(Object.values(BALANCED));
  });

  it('shows the bad-news sample on its guard rails, in the address word EDI keeps, and marks each level the rails move', async () => {
    const fixture = await opened(personalityFixture({ address: 'Captain' }));
    const desk = fixture.nativeElement as HTMLElement;

    button(desk, 'Bad news').click();
    await fixture.whenStable();

    expect(text(desk, '.sr-bubble')).toMatch(/^EDI · bad news\s*Captain, the EDI-31 worker has stopped/);
    expect(text(desk, '.sr-desk__rails')).toBe('Guard rails for bad news: humour 3 → 0, enthusiasm 3 → 0, flavour 5 → 3, directness 6 → 7.');
    expect(texts(desk, '.sr-slider--railed .sr-slider__name')).toEqual(['Humour', 'Directness', 'Enthusiasm', "Ship's flavour"]);
  });

  it('reads the personality again when the window regains focus, unless a change waits to be kept', async () => {
    const fixture = await opened();
    const desk = fixture.nativeElement as HTMLElement;

    window.dispatchEvent(new Event('focus'));
    http.expectOne({ method: 'GET', url: '/api/personality' }).flush(personalityFixture({ levels: PLAIN }));
    await fixture.whenStable();
    expect(sliders(desk).map((slider) => Number(slider.value))).toEqual(Object.values(PLAIN));

    slide(sliders(desk)[0], 6);
    window.dispatchEvent(new Event('focus'));
    http.expectNone({ method: 'GET', url: '/api/personality' });
    await fixture.whenStable();
    expect(sliders(desk)[0].value).toBe('6');
  });

  it('keeps a level the captain slides while the focus read is still on its way', async () => {
    const fixture = await opened();
    const desk = fixture.nativeElement as HTMLElement;

    window.dispatchEvent(new Event('focus'));
    const read = http.expectOne({ method: 'GET', url: '/api/personality' });
    slide(sliders(desk)[0], 9);
    read.flush(personalityFixture({ levels: PLAIN }));
    await fixture.whenStable();

    expect(sliders(desk)[0].value).toBe('9');
    expect(button(desk, 'Keep').disabled).toBe(false);
  });

  it('lets a captain who has chosen no personality keep Balanced as it stands', async () => {
    const desk = (await opened(personalityFixture({ chosen: false }))).nativeElement as HTMLElement;

    expect(button(desk, 'Keep').disabled).toBe(false);
    expect(desk.textContent).toContain('No personality is chosen yet, so Balanced applies until you keep one.');
  });

  it('says that the demo levels apply on top while a demo holds', async () => {
    const fixture = await opened();
    fixture.componentRef.setInput('demoHolds', true);
    await fixture.whenStable();
    const desk = fixture.nativeElement as HTMLElement;

    expect(desk.textContent).toContain("While the demo holds, EDI speaks with the demo's levels on top of these.");
  });

  it('shows the core’s refusal when it does not keep the change', async () => {
    const fixture = await opened(personalityFixture({ chosen: false }));
    const desk = fixture.nativeElement as HTMLElement;

    button(desk, 'Keep').click();
    http.expectOne({ method: 'POST', url: '/api/personality' }).flush('A personality level runs from 0 to 10, not 11.\n', {
      status: 400,
      statusText: 'Bad Request',
    });
    await fixture.whenStable();

    expect(text(desk, '.sr-desk__refusal')).toBe('A personality level runs from 0 to 10, not 11.');
    expect(button(desk, 'Keep').disabled).toBe(false);
  });

  async function opened(personality = personalityFixture()): Promise<ComponentFixture<PersonalityDesk>> {
    const fixture = TestBed.createComponent(PersonalityDesk);
    http.expectOne({ method: 'GET', url: '/api/personality' }).flush(personality);
    await fixture.whenStable();
    return fixture;
  }
});

function sliders(desk: HTMLElement): HTMLInputElement[] {
  return Array.from(desk.querySelectorAll<HTMLInputElement>('.sr-slider input'));
}

function slide(slider: HTMLInputElement, level: number): void {
  slider.value = String(level);
  slider.dispatchEvent(new Event('input'));
}

function button(desk: HTMLElement, label: string): HTMLButtonElement {
  return Array.from(desk.querySelectorAll('button')).find((candidate) => candidate.textContent?.trim() === label) as HTMLButtonElement;
}

function pressed(desk: HTMLElement): string[] {
  return texts(desk, '[aria-pressed="true"]');
}

function text(desk: HTMLElement, selector: string): string {
  return desk.querySelector(selector)?.textContent?.trim() ?? '';
}

function texts(desk: HTMLElement, selector: string): string[] {
  return Array.from(desk.querySelectorAll(selector)).map((element) => element.textContent?.trim() ?? '');
}
