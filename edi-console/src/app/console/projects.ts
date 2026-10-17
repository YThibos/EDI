export function projectsOf(projects: string[], task: string): string {
  return projects.length ? projects.join(' + ') : task;
}
