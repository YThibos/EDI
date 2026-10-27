import { Levels, Scale } from './personality';

export type News = 'good' | 'bad';

export interface RailChange {
  scale: Scale;
  from: number;
  to: number;
}

type AddressPlace = 'message' | 'sentence' | 'closing' | null;

const DRAFT = 'https://github.com/example-owner/harness/pull/42';
const ADDRESS_SHARE_OF_FORMALITY = 0.6;
const DECISION_NEEDED_WEIGHT = 3;
const BAD_NEWS_WEIGHT = 4;
const LIKELY = 0.5;
const BAD_NEWS_RAIL: readonly { scale: Scale; lowest: number; highest: number }[] = [
  { scale: 'humour', lowest: 0, highest: 0 },
  { scale: 'enthusiasm', lowest: 0, highest: 0 },
  { scale: 'flavour', lowest: 0, highest: 3 },
  { scale: 'directness', lowest: 7, highest: 10 },
  { scale: 'verbosity', lowest: 3, highest: 10 },
];
const BAD_NEWS_DECISION = 'D8 needs your word: relaunch with the hint added, which I recommend, or park the task.';

export function sampleMessage(levels: Levels, news: News, address: string): string {
  return news === 'bad' ? badNews(forBadNews(levels), address) : goodNews(levels, address);
}

export function railChanges(levels: Levels): RailChange[] {
  return BAD_NEWS_RAIL.map(({ scale, lowest, highest }) => ({ scale, from: levels[scale], to: Math.min(highest, Math.max(lowest, levels[scale])) }))
    .filter((change) => change.from !== change.to);
}

function badNews(levels: Levels, address: string): string {
  const place = addressed(levels, BAD_NEWS_WEIGHT) ? placeOfTheAddress(levels, 'bad') : null;
  return sentences([
    capitalised(openingAddress(place, address) + badHeadline(levels)),
    'Nothing was pushed and your branches are untouched.',
    badCause(levels),
    levels.verbosity >= 7 ? 'The JVM tests all pass; only the native step fails, after 41 minutes of work.' : null,
    place === 'closing' ? closingTag(BAD_NEWS_DECISION, address) : BAD_NEWS_DECISION,
    levels.initiative >= 7 ? "I can add a native check to the ship's validation so this is caught earlier; say the word." : null,
    levels.warmth >= 7 ? 'The fix itself is small.' : null,
  ]);
}

function forBadNews(levels: Levels): Levels {
  const railed = { ...levels };
  for (const change of railChanges(levels)) {
    railed[change.scale] = change.to;
  }
  return railed;
}

function goodNews(levels: Levels, address: string): string {
  const place = addressed(levels, DECISION_NEEDED_WEIGHT) ? placeOfTheAddress(levels, 'good') : null;
  return (
    systemsReport(levels) +
    sentences([
      `${capitalised(openingAddress(place, address) + goodHeadline(levels))}: ${DRAFT}.`,
      levels.enthusiasm >= 8 ? "It's a clean one!" : null,
      levels.verbosity >= 3 ? goodEvidence(levels) : null,
      humourAside(levels),
      levels.verbosity >= 7 ? 'The worker took 1h12m; the bundle grew by 0.4 kB, and nothing else waits on it.' : null,
      levels.verbosity >= 9 ? 'Next in the queue: EDI-32, the console colours.' : null,
      addressedLater(goodDecision(levels), place, address),
      offerBeyondTheAsk(levels),
      creditToTheCaptain(levels),
      levels.flavour >= 4 ? 'All systems nominal.' : null,
    ])
  );
}

function addressed(levels: Levels, weight: number): boolean {
  return 1 - Math.pow(1 - (levels.formality * ADDRESS_SHARE_OF_FORMALITY) / 10, weight) >= LIKELY;
}

function placeOfTheAddress(levels: Levels, news: News): AddressPlace {
  if (news === 'bad' && levels.formality >= 4) {
    return 'message';
  }
  return levels.formality >= 4 ? 'sentence' : 'closing';
}

function sentences(written: (string | null)[]): string {
  return written.filter((sentence): sentence is string => sentence !== null).join(' ');
}

function capitalised(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1);
}

function openingAddress(place: AddressPlace, address: string): string {
  return place === 'message' ? `${address}, ` : '';
}

function badHeadline(levels: Levels): string {
  return levels.formality <= 3 ? 'the EDI-31 worker stopped: the native build fails.' : 'the EDI-31 worker has stopped: the native build fails.';
}

function badCause(levels: Levels): string {
  return {
    low: 'A setting the native build needs is missing.',
    mid: 'The native build lacks a reflection hint for the new stardate type.',
    high: './mvnw -Pnative verify fails: no reflection hint for ConsoleSitrep.Stardate, which SitrepConsolePresenter now returns.',
  }[band(levels.depth)];
}

function band(level: number): 'low' | 'mid' | 'high' {
  return level <= 3 ? 'low' : level <= 6 ? 'mid' : 'high';
}

function closingTag(sentence: string, address: string): string {
  return sentence.replace(/\.$/, `, ${address}.`);
}

function systemsReport(levels: Levels): string {
  return levels.flavour >= 8 ? 'Stardate 2026.295, systems report. ' : '';
}

function goodHeadline(levels: Levels): string {
  const cheer = levels.enthusiasm >= 7 ? 'excellent news: ' : levels.enthusiasm >= 4 ? 'good news: ' : '';
  if (levels.verbosity <= 2) {
    return cheer + (levels.formality <= 3 ? "EDI-31's ready for review" : 'EDI-31 is ready for review');
  }
  return (
    cheer +
    {
      low: "the stardate header's done and up for review",
      mid: 'the stardate header (EDI-31) is ready for your review',
      high: 'the stardate header (EDI-31) is complete and awaits your review',
    }[band(levels.formality)]
  );
}

function goodEvidence(levels: Levels): string {
  return {
    low: 'Everything checks out.',
    mid: 'All tests and the native build pass.',
    high: '214 JVM tests, ./mvnw -Pnative verify and CodeQL pass; six files changed, all in edi-console.',
  }[band(levels.depth)];
}

function humourAside(levels: Levels): string | null {
  if (levels.humour >= 9) {
    return 'Stardate accurate to the minute; Kirk needed a whole crew for that, you needed one worker.';
  }
  if (levels.humour >= 7) {
    return 'Stardate accurate to the minute; I checked it against three clocks and a very patient test suite.';
  }
  return levels.humour >= 3 ? 'The header now knows what day it is, which is more than the build cache does.' : null;
}

function addressedLater(sentence: string, place: AddressPlace, address: string): string {
  if (place === 'sentence') {
    return `${address}, ${lowerFirstWord(sentence)}`;
  }
  return place === 'closing' ? closingTag(sentence, address) : sentence;
}

function lowerFirstWord(sentence: string): string {
  return /^[A-Z][a-z]/.test(sentence) ? sentence.charAt(0).toLowerCase() + sentence.slice(1) : sentence;
}

function goodDecision(levels: Levels): string {
  const open = levels.formality <= 3 ? "D7's still open" : 'D7 is still open';
  if (levels.verbosity <= 2) {
    return levels.directness >= 7 ? 'D7: take ship time.' : `${open}: local or ship time; I recommend ship time.`;
  }
  return {
    low: `${open}: local or ship time for the header. Both are reasonable; I lean towards ship time, since the console shows UTC elsewhere.`,
    mid: `${open}: local or ship time for the header; I recommend ship time, as the console shows UTC everywhere else.`,
    high: 'D7 needs your word: take ship time; local time would contradict the rest of the console.',
  }[band(levels.directness)];
}

function offerBeyondTheAsk(levels: Levels): string | null {
  if (levels.initiative >= 7) {
    return 'Next I suggest EDI-32 and EDI-35, which share the colour tokens; I can draft both specs for your go.';
  }
  return levels.initiative >= 4 ? 'Shall I queue the console colours (EDI-32) next?' : null;
}

function creditToTheCaptain(levels: Levels): string | null {
  if (levels.warmth >= 7) {
    return 'Your call on D6 saved a day of rework; thank you, that was a good one.';
  }
  return levels.warmth >= 4 ? 'Your answer on D6 is what unblocked it.' : null;
}
