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
  answered: AnsweredDecision[];
  ongoing: OngoingTask[];
  landed: LandedTask[];
  landedTotal: number;
  charted: ChartedTask[];
  chartedTotal: number;
}

export interface PresenceShown {
  mode: 'present' | 'quiet' | 'away';
  demo: boolean;
  words: string;
}

export type PresenceChoice = 'present' | 'demo' | 'demo-off';

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
}

export interface ReadyForReview {
  task: string;
  title: string;
  projects: string[];
  url: string;
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
