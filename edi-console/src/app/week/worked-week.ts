export interface WorkedWeek {
  from: string;
  thisWeek: string;
  zone: string;
  days: string[];
  tasks: WorkedTask[];
  efforts: YieldAtAnEffort[];
}

export interface WorkedTask {
  task: string;
  title: string;
  key: string | null;
  bookedOn: string | null;
  days: WorkedDay[];
}

export interface WorkedDay {
  day: string;
  activeMinutes: number;
  wallClockMinutes: number;
}

export interface YieldAtAnEffort {
  effort: string | null;
  tasks: number;
  weightedWorkerTokens: number;
  weightedWorkerTokensPerTask: number;
  weightedPipelineTokens: number;
  reviewRounds: number;
  findings: number;
  findingsPerReviewRound: number | null;
}

export function hoursAndMinutes(minutes: number): string {
  return `${Math.floor(minutes / 60)}h${String(minutes % 60).padStart(2, '0')}m`;
}

export function totalActive(task: WorkedTask): number {
  return task.days.reduce((sum, day) => sum + day.activeMinutes, 0);
}

export function workedOn(task: WorkedTask, day: string): WorkedDay | undefined {
  return task.days.find((worked) => worked.day === day);
}

export function timesheetLines(task: WorkedTask): string {
  const work = task.key ? `${task.key} ${task.title}` : task.title;
  return task.days
    .filter((day) => day.activeMinutes >= 1)
    .map((day) => [day.day, task.bookedOn ?? task.key ?? '', jiraDuration(day.activeMinutes), work].join('\t'))
    .join('\n');
}

export function jiraDuration(minutes: number): string {
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  if (hours && rest) {
    return `${hours}h ${rest}m`;
  }
  return hours ? `${hours}h` : `${rest}m`;
}

export function weekAround(monday: string, weeks: number): string {
  const day = new Date(`${monday}T12:00:00Z`);
  day.setUTCDate(day.getUTCDate() + 7 * weeks);
  return day.toISOString().slice(0, 10);
}

export function effortName(yieldAtAnEffort: YieldAtAnEffort): string {
  return yieldAtAnEffort.effort ?? 'no flag';
}

export function weightedTokens(tokens: number): string {
  const thousands = Math.round(tokens / 1_000);
  if (thousands >= 1_000) {
    return `${(tokens / 1_000_000).toFixed(1)}M`;
  }
  return thousands >= 1 ? `${thousands}k` : String(tokens);
}

export function findingsPerRound(yieldAtAnEffort: YieldAtAnEffort): string {
  return yieldAtAnEffort.findingsPerReviewRound === null ? '-' : yieldAtAnEffort.findingsPerReviewRound.toFixed(2);
}
