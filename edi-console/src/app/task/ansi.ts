export interface Segment {
  text: string;
  colour: string | null;
  background: string | null;
  bold: boolean;
  dim: boolean;
}

interface Pen {
  colour: string | null;
  background: string | null;
  bold: boolean;
  dim: boolean;
}

const BASIC = ['#1c2a52', '#ff6479', '#3ee0a1', '#ffb84d', '#7aa7ff', '#8f7dff', '#56c8ff', '#b9c6ea'];
const BRIGHT = ['#65739f', '#ff8597', '#6ff0bd', '#ffd08a', '#a3c2ff', '#b3a6ff', '#8ad9ff', '#eaf0ff'];
const SGR = /\u001b\[([0-9;]*)m/g;
const OTHER_ESCAPES = /\u001b\[[0-9;?]*[A-Za-ln-z]|\u001b[()][0-9A-Za-z]|\u001b[=>]/g;

export function segmentsOf(line: string): Segment[] {
  const segments: Segment[] = [];
  let pen: Pen = { colour: null, background: null, bold: false, dim: false };
  let at = 0;
  const clean = line.replace(OTHER_ESCAPES, '');
  for (const match of clean.matchAll(SGR)) {
    pushed(segments, clean.slice(at, match.index), pen);
    pen = penAfter(pen, match[1]);
    at = (match.index ?? 0) + match[0].length;
  }
  pushed(segments, clean.slice(at), pen);
  return segments;
}

function pushed(segments: Segment[], text: string, pen: Pen): void {
  if (text) {
    segments.push({ text, ...pen });
  }
}

function penAfter(pen: Pen, parameters: string): Pen {
  const codes = parameters === '' ? [0] : parameters.split(';').map(Number);
  let next = { ...pen };
  for (let at = 0; at < codes.length; at++) {
    const code = codes[at];
    if (code === 38 || code === 48) {
      const [colour, used] = extendedColour(codes, at + 1);
      next = code === 38 ? { ...next, colour } : { ...next, background: colour };
      at += used;
    } else {
      next = penWith(next, code);
    }
  }
  return next;
}

function penWith(pen: Pen, code: number): Pen {
  if (code === 0) {
    return { colour: null, background: null, bold: false, dim: false };
  }
  if (code === 1 || code === 2 || code === 22) {
    return { ...pen, bold: code === 1, dim: code === 2 };
  }
  if (code === 39 || code === 49) {
    return code === 39 ? { ...pen, colour: null } : { ...pen, background: null };
  }
  const colour = basicColour(code);
  if (colour) {
    return code < 40 || (code >= 90 && code < 100) ? { ...pen, colour } : { ...pen, background: colour };
  }
  return pen;
}

function basicColour(code: number): string | null {
  if ((code >= 30 && code <= 37) || (code >= 40 && code <= 47)) {
    return BASIC[code % 10];
  }
  if ((code >= 90 && code <= 97) || (code >= 100 && code <= 107)) {
    return BRIGHT[code % 10];
  }
  return null;
}

function extendedColour(codes: number[], at: number): [string | null, number] {
  if (codes[at] === 2 && codes.length >= at + 4) {
    return [`rgb(${codes[at + 1]}, ${codes[at + 2]}, ${codes[at + 3]})`, 4];
  }
  if (codes[at] === 5 && codes.length >= at + 2) {
    return [paletteColour(codes[at + 1]), 2];
  }
  return [null, 0];
}

function paletteColour(index: number): string {
  if (index < 8) {
    return BASIC[index];
  }
  if (index < 16) {
    return BRIGHT[index - 8];
  }
  if (index < 232) {
    const cube = index - 16;
    const level = (step: number) => (step === 0 ? 0 : 55 + step * 40);
    return `rgb(${level(Math.floor(cube / 36))}, ${level(Math.floor(cube / 6) % 6)}, ${level(cube % 6)})`;
  }
  const grey = 8 + (index - 232) * 10;
  return `rgb(${grey}, ${grey}, ${grey})`;
}
