const DAY = 86_400_000;
const INSTANT = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/;

interface ShipClock {
  year: string;
  month: string;
  day: string;
  hour: string;
  minute: string;
  timeZoneName: string;
}

export function stardate(stamp: string, zone: string): string {
  const clock = shipClockAt(stamp, zone);
  return clock ? `${starday(clock)} · ${timeOfDay(clock)}` : stamp || '-';
}

export function shipTime(stamp: string, zone: string): string {
  const clock = shipClockAt(stamp, zone);
  return clock ? timeOfDay(clock) : stamp;
}

export function shipMoment(stamp: string, zone: string): string {
  const clock = shipClockAt(stamp, zone);
  return clock ? `${clock.year}-${clock.month}-${clock.day} ${timeOfDay(clock)}` : stamp;
}

function shipClockAt(stamp: string, zone: string): ShipClock | null {
  if (!INSTANT.test(stamp)) {
    return null;
  }
  const parts = shipClockFormat(zone).formatToParts(new Date(stamp));
  return Object.fromEntries(parts.map((part) => [part.type, part.value])) as unknown as ShipClock;
}

function shipClockFormat(zone: string): Intl.DateTimeFormat {
  return new Intl.DateTimeFormat('en-GB', {
    timeZone: zone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
    timeZoneName: 'short',
  });
}

function starday(clock: ShipClock): string {
  const dayOfYear = Math.floor((Date.UTC(+clock.year, +clock.month - 1, +clock.day) - Date.UTC(+clock.year, 0, 0)) / DAY);
  return `SD ${clock.year}.${String(dayOfYear).padStart(3, '0')}`;
}

function timeOfDay(clock: ShipClock): string {
  return `${clock.hour}:${clock.minute} ${clock.timeZoneName}`;
}
