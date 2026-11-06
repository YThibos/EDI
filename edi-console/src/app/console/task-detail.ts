export interface TaskDetail {
  task: {
    id: string;
    intake: {
      kind: string;
      title: string;
      projects: string[];
      workKey: string | null;
      base: string | null;
      blockedBy: string[];
      delivery: string | null;
    };
    state: string;
    notes: string;
    hold: { kind: string; reason: string; until: string | null; setAt: string } | null;
    created: string;
  };
  blockers: { id: string; state: string }[];
  decision: TaskDecision | null;
  delivery: { address: { number: number }; draft: boolean; state: string } | null;
  worked: { active: string; wallClock: string } | null;
  timeline: TimelineEvent[];
}

export interface TaskDecision {
  number: string;
  card: {
    title: string;
    context: string;
    options: { key: string; text: string }[];
    recommendation: { key: string; why: string } | null;
  };
  raisedAt: string;
  answer: { verbatim: string; option: string | null; answeredAt: string; channel: string } | null;
  open: boolean;
}

export interface TimelineEvent {
  sequence: number;
  at: string;
  actor: string;
  type: string;
}

export interface AnsweredRounds {
  task: string;
  rounds: {
    number: number;
    answeredAt: string;
    answers: { decision: string | null; verbatim: string; option: string | null; refusedBecause: string | null }[];
  }[];
}

export type TaskPageName = 'brief' | 'report';
