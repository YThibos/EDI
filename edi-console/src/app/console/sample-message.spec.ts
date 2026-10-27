import { BALANCED, PLAIN } from './personality.fixture';
import { railChanges, sampleMessage } from './sample-message';

describe('sampleMessage', () => {
  it('tells good news at Balanced with a dry aside, an offer and the address word opening a later sentence', () => {
    expect(sampleMessage(BALANCED, 'good', 'Commander')).toBe(
      'The stardate header (EDI-31) is ready for your review: https://github.com/example-owner/harness/pull/42. ' +
        'All tests and the native build pass. The header now knows what day it is, which is more than the build cache does. ' +
        'Commander, D7 is still open: local or ship time for the header; I recommend ship time, as the console shows UTC everywhere else. ' +
        'Shall I queue the console colours (EDI-32) next? Your answer on D6 is what unblocked it. All systems nominal.',
    );
  });

  it('keeps Plain to the facts and a blunt recommendation', () => {
    expect(sampleMessage(PLAIN, 'good', 'Commander')).toBe(
      'The stardate header (EDI-31) is ready for your review: https://github.com/example-owner/harness/pull/42. ' +
        'All tests and the native build pass. Commander, D7 needs your word: take ship time; local time would contradict the rest of the console.',
    );
  });

  it('tells bad news on the rail, opening with the address word from formality 4', () => {
    expect(sampleMessage({ ...BALANCED, humour: 10, enthusiasm: 10 }, 'bad', 'Captain')).toBe(
      'Captain, the EDI-31 worker has stopped: the native build fails. Nothing was pushed and your branches are untouched. ' +
        'The native build lacks a reflection hint for the new stardate type. ' +
        'D8 needs your word: relaunch with the hint added, which I recommend, or park the task.',
    );
  });

  it('puts the address word in a closing tag at low formality, and never uses it at formality 0', () => {
    expect(sampleMessage({ ...BALANCED, formality: 3 }, 'bad', 'Commander')).toContain('or park the task, Commander.');
    expect(sampleMessage({ ...BALANCED, formality: 0 }, 'good', 'Commander')).not.toContain('Commander');
    expect(sampleMessage({ ...BALANCED, formality: 0 }, 'bad', 'Commander')).not.toContain('Commander');
  });

  it('names every level the bad-news rail moves, and only those', () => {
    expect(railChanges(BALANCED)).toEqual([
      { scale: 'humour', from: 3, to: 0 },
      { scale: 'enthusiasm', from: 3, to: 0 },
      { scale: 'flavour', from: 5, to: 3 },
      { scale: 'directness', from: 6, to: 7 },
    ]);
    expect(railChanges(PLAIN)).toEqual([]);
  });
});
