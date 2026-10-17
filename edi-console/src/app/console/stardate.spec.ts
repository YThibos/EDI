import { shipTime, stardate } from './stardate';

describe('stardate', () => {
  it('reads a moment as the year, the day of the year and the time in UTC', () => {
    expect(stardate('2026-10-15T06:17:35.120Z')).toBe('SD 2026.288 · 06:17Z');
    expect(stardate('2026-01-01T00:00:00Z')).toBe('SD 2026.001 · 00:00Z');
  });

  it('shows anything it cannot read as given', () => {
    expect(stardate('')).toBe('-');
    expect(stardate('soon')).toBe('soon');
  });

  it('gives the ship time of a moment', () => {
    expect(shipTime('2026-10-15T05:40:00Z')).toBe('05:40Z');
  });
});
