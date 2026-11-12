import { toneOf } from './tone';

describe('toneOf', () => {
  it('marks a stage that waits on the captain amber', () => {
    expect(toneOf('delivered')).toBe('warn');
    expect(toneOf('ready')).toBe('warn');
  });

  it('marks a merged, landed or retired task green', () => {
    expect(toneOf('merged')).toBe('online');
    expect(toneOf('landed')).toBe('online');
    expect(toneOf('retired')).toBe('online');
  });

  it('marks a task in changes ion blue, not red', () => {
    expect(toneOf('changing')).toBe('info');
  });

  it('marks a dropped task neutral', () => {
    expect(toneOf('dropped')).toBe('neutral');
  });
});
