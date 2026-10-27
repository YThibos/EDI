export type Scale = 'humour' | 'formality' | 'verbosity' | 'warmth' | 'directness' | 'initiative' | 'depth' | 'enthusiasm' | 'flavour';

export type Levels = Record<Scale, number>;

export interface PersonalityShown {
  address: string;
  chosen: boolean;
  levels: Levels;
  presets: PresetShown[];
}

export interface PresetShown {
  name: string;
  levels: Levels;
}

export const SCALES: readonly { scale: Scale; name: string }[] = [
  { scale: 'humour', name: 'Humour' },
  { scale: 'formality', name: 'Formality' },
  { scale: 'verbosity', name: 'Verbosity' },
  { scale: 'warmth', name: 'Warmth' },
  { scale: 'directness', name: 'Directness' },
  { scale: 'initiative', name: 'Initiative' },
  { scale: 'depth', name: 'Technical depth' },
  { scale: 'enthusiasm', name: 'Enthusiasm' },
  { scale: 'flavour', name: "Ship's flavour" },
];

export function sameLevels(one: Levels, other: Levels): boolean {
  return SCALES.every(({ scale }) => one[scale] === other[scale]);
}

export function personalityChange(levels: Levels): string[] {
  return SCALES.flatMap(({ scale }) => [scale, String(levels[scale])]);
}
