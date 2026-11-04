export interface Sitrep {
  schema: 'edi-sitrep.v1';
  generated: string;
  zone: string;
  machine: string;
  address: string;
  presence: PresenceShown;
  decisions: DecisionCard[];
  boards: WaitingBoard[];
  reviews: ReadyForReview[];
  changing: ChangingRequest[];
  workerQuestions: WorkerQuestion[];
  answered: AnsweredDecision[];
  ongoing: OngoingTask[];
  landed: LandedTask[];
  landedTotal: number;
  charted: ChartedTask[];
  chartedTotal: number;
  links?: Record<string, TaskLinks>;
}

export interface TaskLinks {
  key: string | null;
  tracker: string | null;
  request: { url: string; number: number; state: string; step: string | null } | null;
  branch: { name: string; url: string | null } | null;
  board: string | null;
  report: string | null;
  decisions: string[];
}

export interface PresenceShown {
  mode: 'present' | 'quiet' | 'away';
  demo: boolean;
  words: string;
}

export type PresenceChoice = 'present' | 'quiet' | 'demo' | 'demo-off';

export interface DecisionCard {
  number: string;
  task: string;
  projects: string[];
  title: string;
  context: string;
  options: DecisionOption[];
  recommended: Recommendation;
  raisedAt: string;
}

export interface DecisionOption {
  key: string;
  text: string;
}

export interface Recommendation {
  key: string;
  why: string;
}

export interface WaitingBoard {
  task: string;
  title: string;
  projects: string[];
  questions: number;
  link: string;
  decisions: BoardDecision[];
}

export interface BoardDecision {
  number: string;
  title: string;
}

export interface ReadyForReview {
  task: string;
  title: string;
  projects: string[];
  url: string;
}

export interface ChangingRequest {
  task: string;
  title: string;
  projects: string[];
  url: string;
  why: string;
}

export interface WorkerQuestion {
  task: string;
  title: string;
  projects: string[];
  question: string;
  askedAt: string;
}

export interface AnsweredDecision {
  number: string;
  task: string;
  projects: string[];
  title: string;
  verbatim: string;
  option: string | null;
  channel: string;
  answeredAt: string;
}

export interface OngoingTask {
  task: string;
  key: string | null;
  title: string;
  kind: string;
  projects: string[];
  state: string;
  doing: string;
  draft: string | null;
}

export interface LandedTask {
  task: string;
  title: string;
  kind: string;
  projects: string[];
  landedAt: string;
  draft: string | null;
}

export interface ChartedTask {
  task: string;
  title: string;
  kind: string;
  projects: string[];
  waiting: string | null;
}

export interface GivenAnswer {
  option: string | null;
  text: string;
}
