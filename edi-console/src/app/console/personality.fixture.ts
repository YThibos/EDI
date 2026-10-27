import { Levels, PersonalityShown } from './personality';

export const BALANCED: Levels = { humour: 3, formality: 5, verbosity: 5, warmth: 4, directness: 6, initiative: 5, depth: 5, enthusiasm: 3, flavour: 5 };
export const PLAIN: Levels = { humour: 0, formality: 5, verbosity: 3, warmth: 2, directness: 7, initiative: 3, depth: 5, enthusiasm: 0, flavour: 0 };
export const DEMO: Levels = { humour: 3, formality: 7, verbosity: 5, warmth: 4, directness: 6, initiative: 5, depth: 4, enthusiasm: 3, flavour: 7 };

export function personalityFixture(changes: Partial<PersonalityShown> = {}): PersonalityShown {
  return {
    address: 'Commander',
    chosen: true,
    levels: BALANCED,
    presets: [
      { name: 'Balanced', levels: BALANCED },
      { name: 'Plain', levels: PLAIN },
      { name: 'Demo', levels: DEMO },
    ],
    ...changes,
  };
}
