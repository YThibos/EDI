export type Tone = 'online' | 'warn' | 'danger' | 'info' | 'neutral';

const TONES: Record<string, Tone> = {
  working: 'online',
  done: 'online',
  paused: 'warn',
  waiting: 'warn',
  blocked: 'danger',
  failed: 'danger',
  starting: 'info',
  'with me': 'info',
  busy: 'online',
  idle: 'neutral',
  delivered: 'warn',
  ready: 'warn',
  changing: 'info',
  merged: 'online',
  landed: 'online',
  retired: 'online',
  dropped: 'neutral',
};

export function toneOf(state: string): Tone {
  return TONES[state] ?? 'info';
}
