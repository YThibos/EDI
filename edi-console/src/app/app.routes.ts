import { Routes } from '@angular/router';

import { SignInPage } from './sign-in/sign-in-page';
import { SitrepPage } from './sitrep/sitrep-page';
import { TaskPanel } from './task/task-panel';
import { EmptyView } from './views/empty-view';

export const routes: Routes = [
  {
    path: '',
    component: SitrepPage,
    title: 'SITREP · EDI',
    children: [
      { path: '', children: [] },
      { path: 'task/:id', component: TaskPanel },
    ],
  },
  { path: 'fleet', component: EmptyView, title: 'FLEET · EDI', data: { view: 'Fleet', shows: 'the workers, my health and the usage' } },
  { path: 'week', component: EmptyView, title: 'WEEK · EDI', data: { view: 'Week', shows: 'the active time on each task this week' } },
  { path: 'login', component: SignInPage, title: 'Sign in · EDI' },
  { path: '**', redirectTo: '' },
];
