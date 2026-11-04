import { Sitrep, WaitingBoard } from './sitrep';

export function waitingOn(sitrep: Sitrep | null): number {
  return sitrep ? sitrep.decisions.length + openOnBoards(sitrep.boards) + sitrep.reviews.length + sitrep.changing.length : 0;
}

function openOnBoards(boards: WaitingBoard[]): number {
  return boards.reduce((open, board) => open + (board.questions > 0 ? 1 : 0) + board.decisions.length, 0);
}
