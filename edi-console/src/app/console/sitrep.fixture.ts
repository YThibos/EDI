import { Sitrep } from './sitrep';

export function sitrepFixture(changes: Partial<Sitrep> = {}): Sitrep {
  return {
    schema: 'edi-sitrep.v1',
    generated: '2026-10-15T06:17:35.120Z',
    machine: 'laptop',
    address: 'Commander',
    presence: { mode: 'quiet', demo: false, words: 'quiet since 2026-10-15 06:00Z, after a stretch without a typed message' },
    decisions: [
      {
        number: 'D58',
        task: 'lamp-choice',
        projects: ['harness'],
        title: 'Which lamp?',
        context: 'Both work; one is simpler.',
        options: [
          { key: 'A', text: 'The simple way' },
          { key: 'B', text: 'The thorough way' },
        ],
        recommended: { key: 'A', why: 'it is enough' },
        raisedAt: '2026-10-15T06:00:00Z',
      },
    ],
    answered: [
      {
        number: 'D57',
        task: 'horn-choice',
        projects: [],
        title: 'Which horn?',
        verbatim: 'B, and tune it lower',
        option: 'B',
        channel: 'console',
        answeredAt: '2026-10-15T05:40:00Z',
      },
    ],
    ongoing: [
      { task: 'fit-lamp', title: 'Fit the lamp', kind: 'ship', projects: ['harness', 'garden'], state: 'working', doing: 'pipeline review', draft: null },
    ],
    landed: [
      {
        task: 'paint-buoy',
        title: 'Paint the buoy',
        kind: 'ship',
        projects: ['harness'],
        landedAt: '2026-10-14T16:02:00Z',
        draft: 'https://github.com/example-owner/harness/pull/12',
      },
    ],
    landedTotal: 1,
    charted: [
      { task: 'tune-horn', title: 'Tune the horn', kind: 'ship', projects: ['harness'], waiting: 'deferred until 2026-11-01' },
      { task: 'scrub-deck', title: 'Scrub the deck', kind: 'ship', projects: ['harness'], waiting: null },
    ],
    chartedTotal: 23,
    ...changes,
  };
}
