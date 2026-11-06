export interface WorkedWeek {
  from: string;
  zone: string;
  days: string[];
  tasks: WorkedTask[];
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

export function hoursAndMinutes(minutes: number): string {
  return `${Math.floor(minutes / 60)}h${String(minutes % 60).padStart(2, '0')}m`;
}

export function totalActive(task: WorkedTask): number {
  return task.days.reduce((sum, day) => sum + day.activeMinutes, 0);
}

export function workedOn(task: WorkedTask, day: string): WorkedDay | undefined {
  return task.days.find((worked) => worked.day === day);
}
