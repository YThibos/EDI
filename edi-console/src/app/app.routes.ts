import { Routes } from '@angular/router';

import { SignInPage } from './sign-in/sign-in-page';
import { SitrepPage } from './sitrep/sitrep-page';
import { TaskPanel } from './task/task-panel';
import { WeekView } from './week/week-view';
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
  { path: 'week', component: WeekView, title: 'WEEK · EDI' },
  { path: 'login', component: SignInPage, title: 'Sign in · EDI' },
  { path: '**', redirectTo: '' },
];
