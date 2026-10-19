import { Routes } from '@angular/router';

import { SignInPage } from './sign-in/sign-in-page';
import { SitrepPage } from './sitrep/sitrep-page';

export const routes: Routes = [
  { path: '', component: SitrepPage, title: 'SITREP · EDI' },
  { path: 'login', component: SignInPage, title: 'Sign in · EDI' },
  { path: '**', redirectTo: '' },
];
