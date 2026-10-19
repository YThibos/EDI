import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { ConsoleApi } from '../console/console-api';
import { ConsoleHeader } from '../console/console-header';

@Component({
  selector: 'app-sign-in-page',
  imports: [ConsoleHeader],
  templateUrl: './sign-in-page.html',
  styles: ':host { display: block; min-height: 100vh; }',
})
export class SignInPage {
  private readonly api = inject(ConsoleApi);
  private readonly router = inject(Router);

  protected readonly refused = signal(false);

  constructor() {
    const token = window.location.hash.slice(1);
    history.replaceState(null, '', window.location.pathname);
    if (!token) {
      this.refused.set(true);
      return;
    }
    this.api.signIn(token).subscribe({
      next: () => this.router.navigateByUrl('/', { replaceUrl: true }),
      error: () => this.refused.set(true),
    });
  }
}
