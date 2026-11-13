import { DecisionCard, Sitrep } from './sitrep';

export function waitingOn(sitrep: Sitrep | null): number {
  return sitrep ? cardsOffTheBoards(sitrep).length + sitrep.boards.length + sitrep.reviews.length + sitrep.changing.length : 0;
}

export function cardsOffTheBoards(sitrep: Sitrep): DecisionCard[] {
  const onABoard = new Set(sitrep.boards.flatMap((board) => board.decisions.map((decision) => decision.number)));
  return sitrep.decisions.filter((card) => !onABoard.has(card.number));
}
