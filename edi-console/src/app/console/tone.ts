export type Tone = 'online' | 'warn' | 'danger' | 'info' | 'neutral';

const TONES: Record<string, Tone> = {
  working: 'online',
  done: 'online',
  'needs-decision': 'warn',
  paused: 'warn',
  waiting: 'warn',
  blocked: 'danger',
  failed: 'danger',
  starting: 'info',
  busy: 'online',
  idle: 'neutral',
};

export function toneOf(state: string): Tone {
  return TONES[state] ?? 'info';
}
