const DAY = 86_400_000;
const STAMP = /^(\d{4})-(\d{2})-(\d{2})(?:T(\d{2}):(\d{2}))?/;

export function stardate(stamp: string): string {
  const parts = STAMP.exec(stamp);
  if (!parts) {
    return stamp || '-';
  }
  const [, year, month, day, hours, minutes] = parts;
  const dayOfYear = Math.floor((Date.UTC(+year, +month - 1, +day) - Date.UTC(+year, 0, 0)) / DAY);
  const date = `SD ${year}.${String(dayOfYear).padStart(3, '0')}`;
  return hours ? `${date} · ${hours}:${minutes}Z` : date;
}

export function shipTime(stamp: string): string {
  const parts = STAMP.exec(stamp);
  return parts?.[4] ? `${parts[4]}:${parts[5]}Z` : stamp;
}
