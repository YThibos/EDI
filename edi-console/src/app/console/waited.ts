export function waitedSince(since: string, now: string | null): string {
  const minutes = Math.max(0, Math.floor(((now ? Date.parse(now) : Date.now()) - Date.parse(since)) / 60000));
  if (minutes === 0) {
    return 'under a minute';
  }
  if (minutes < 60) {
    return counted(minutes, 'minute');
  }
  if (minutes < 48 * 60) {
    const rest = minutes % 60;
    return counted(Math.floor(minutes / 60), 'hour') + (rest ? ` ${counted(rest, 'minute')}` : '');
  }
  return counted(Math.floor(minutes / (24 * 60)), 'day');
}

function counted(count: number, noun: string): string {
  return `${count} ${noun}${count === 1 ? '' : 's'}`;
}
