import { readFile, writeFile } from 'node:fs/promises';
import { Resvg } from '@resvg/resvg-js';

const publicFiles = new URL('public/', import.meta.url);
const hologram = await readFile(new URL('favicon.svg', publicFiles));
const fallbacks = { 'favicon-32.png': 32, 'apple-touch-icon.png': 180 };

for (const [name, width] of Object.entries(fallbacks)) {
  const png = new Resvg(hologram, { fitTo: { mode: 'width', value: width } }).render().asPng();
  await writeFile(new URL(name, publicFiles), png);
}
