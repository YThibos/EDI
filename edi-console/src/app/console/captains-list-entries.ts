import { AnsweredDecision, DecisionCard, EndedBoard, ReadyForReview, SettledReview, Sitrep, WaitingBoard } from './sitrep';
import { cardsOffTheBoards } from './waiting';

export type ListEntry =
  | { kind: 'decision'; key: string; dealtWith: false; card: DecisionCard }
  | { kind: 'board'; key: string; dealtWith: false; board: WaitingBoard }
  | { kind: 'review'; key: string; dealtWith: false; review: ReadyForReview }
  | { kind: 'answered'; key: string; dealtWith: true; answer: AnsweredDecision }
  | { kind: 'ended-board'; key: string; dealtWith: true; board: EndedBoard }
  | { kind: 'settled-review'; key: string; dealtWith: true; review: SettledReview };

export function entriesOf(sitrep: Sitrep): ListEntry[] {
  const decisions = cardsOffTheBoards(sitrep).map(decisionEntry);
  return [
    ...decisions.filter(stopsAWorker),
    ...decisions.filter((entry) => !stopsAWorker(entry)),
    ...sitrep.boards.map(boardEntry),
    ...sitrep.reviews.map(reviewEntry),
    ...sitrep.dealtWith.decisions.map(answeredEntry),
    ...sitrep.dealtWith.boards.map(endedBoardEntry),
    ...sitrep.dealtWith.reviews.map(settledReviewEntry),
  ];
}

export function stopsAWorker(entry: ListEntry): boolean {
  return entry.kind === 'decision' && !!entry.card.stops;
}

export function opensFurther(entry: ListEntry): boolean {
  switch (entry.kind) {
    case 'decision':
    case 'answered':
      return true;
    case 'review':
    case 'settled-review':
      return entry.review.afterLanding.steps.length > 0 || !!entry.review.afterLanding.landingOrder;
    case 'board':
    case 'ended-board':
      return false;
  }
}

export function callOf(entry: ListEntry): string | null {
  switch (entry.kind) {
    case 'answered':
      return entry.answer.call;
    case 'ended-board':
      return entry.board.call;
    case 'settled-review':
      return entry.review.call;
    case 'decision':
    case 'board':
    case 'review':
      return null;
  }
}

function decisionEntry(card: DecisionCard): ListEntry {
  return { kind: 'decision', key: card.number, dealtWith: false, card };
}

function boardEntry(board: WaitingBoard): ListEntry {
  return { kind: 'board', key: `board-of:${board.task}`, dealtWith: false, board };
}

function reviewEntry(review: ReadyForReview): ListEntry {
  return { kind: 'review', key: `review:${review.task}`, dealtWith: false, review };
}

function answeredEntry(answer: AnsweredDecision): ListEntry {
  return { kind: 'answered', key: answer.number, dealtWith: true, answer };
}

function endedBoardEntry(board: EndedBoard): ListEntry {
  return { kind: 'ended-board', key: board.call, dealtWith: true, board };
}

function settledReviewEntry(review: SettledReview): ListEntry {
  return { kind: 'settled-review', key: review.call, dealtWith: true, review };
}
