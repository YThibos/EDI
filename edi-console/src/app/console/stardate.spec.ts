import { shipMoment, shipTime, stardate } from './stardate';

const BRUSSELS = 'Europe/Brussels';

describe('stardate', () => {
  it('reads a moment as the year, the day of the year and the time in the machine’s zone', () => {
    expect(stardate('2026-10-15T06:17:35.120Z', BRUSSELS)).toBe('SD 2026.288 · 08:17 CEST');
    expect(stardate('2026-01-01T00:00:00Z', BRUSSELS)).toBe('SD 2026.001 · 01:00 CET');
  });

  it('follows the machine’s zone across the clocks going back', () => {
    expect(stardate('2026-10-25T00:59:00Z', BRUSSELS)).toBe('SD 2026.298 · 02:59 CEST');
    expect(stardate('2026-10-25T01:00:00Z', BRUSSELS)).toBe('SD 2026.298 · 02:00 CET');
  });

  it('counts the day of the year on the machine’s calendar', () => {
    expect(stardate('2026-12-31T23:30:00Z', BRUSSELS)).toBe('SD 2027.001 · 00:30 CET');
    expect(stardate('2026-12-31T23:30:00Z', 'UTC')).toBe('SD 2026.365 · 23:30 UTC');
  });

  it('reads a zone outside Brussels', () => {
    expect(stardate('2026-10-25T00:59:00Z', 'Europe/London')).toBe('SD 2026.298 · 01:59 BST');
    expect(stardate('2026-10-25T01:00:00Z', 'Europe/London')).toBe('SD 2026.298 · 01:00 GMT');
  });

  it('shows anything it cannot read as given', () => {
    expect(stardate('', BRUSSELS)).toBe('-');
    expect(stardate('soon', BRUSSELS)).toBe('soon');
    expect(shipTime('soon', BRUSSELS)).toBe('soon');
  });

  it('gives the ship time and the ship moment of a moment', () => {
    expect(shipTime('2026-10-15T05:40:00Z', BRUSSELS)).toBe('07:40 CEST');
    expect(shipMoment('2026-10-15T05:40:00Z', BRUSSELS)).toBe('2026-10-15 07:40 CEST');
  });
});
