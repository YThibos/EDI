import { segmentsOf } from './ansi';

describe('segmentsOf', () => {
  it('splits a line at its colour codes and resets at 0', () => {
    expect(segmentsOf('\u001b[31mred\u001b[0m plain')).toEqual([
      { text: 'red', colour: '#ff6479', background: null, bold: false, dim: false },
      { text: ' plain', colour: null, background: null, bold: false, dim: false },
    ]);
  });

  it('reads bold, bright, background, 256-colour and true-colour codes', () => {
    expect(segmentsOf('\u001b[1;92mok\u001b[22;44m bg\u001b[38;5;196m hot\u001b[38;2;10;20;30m rgb\u001b[39;49m')).toEqual([
      { text: 'ok', colour: '#6ff0bd', background: null, bold: true, dim: false },
      { text: ' bg', colour: '#6ff0bd', background: '#7aa7ff', bold: false, dim: false },
      { text: ' hot', colour: 'rgb(255, 0, 0)', background: '#7aa7ff', bold: false, dim: false },
      { text: ' rgb', colour: 'rgb(10, 20, 30)', background: '#7aa7ff', bold: false, dim: false },
    ]);
  });

  it('drops escapes that are not colours and keeps a line without any as it is', () => {
    expect(segmentsOf('\u001b[?25lcursor\u001b[K gone')).toEqual([{ text: 'cursor gone', colour: null, background: null, bold: false, dim: false }]);
    expect(segmentsOf('❯ plain')).toEqual([{ text: '❯ plain', colour: null, background: null, bold: false, dim: false }]);
    expect(segmentsOf('')).toEqual([]);
  });
});
