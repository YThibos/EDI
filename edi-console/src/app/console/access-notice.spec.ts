import { Component, input } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { Access } from './access';
import { AccessNotice } from './access-notice';

@Component({
  imports: [AccessNotice],
  template: '<app-access-notice [access]="access()"><p class="view">The view</p></app-access-notice>',
})
class NoticeHost {
  readonly access = input.required<Access>();
}

describe('AccessNotice', () => {
  it('asks the captain to sign in when the console has no session', async () => {
    const shown = await noticeFor('signed-out');

    expect(shown.querySelector('.sr-notice__title')?.textContent).toBe('Sign in to see the sitrep');
    expect(shown.querySelector('.view')).toBeNull();
  });

  it('tells the captain the core is not answering', async () => {
    const shown = await noticeFor('unreachable');

    expect(shown.querySelector('.sr-notice__title')?.textContent).toBe('My core is not answering');
    expect(shown.querySelector('.view')).toBeNull();
  });

  it('shows the view itself while it loads and once the captain is signed in', async () => {
    const loading = await noticeFor('loading');
    const signedIn = await noticeFor('signed-in');

    expect(loading.querySelector('.view')?.textContent).toBe('The view');
    expect(signedIn.querySelector('.view')?.textContent).toBe('The view');
    expect(signedIn.querySelector('.sr-notice')).toBeNull();
  });

  async function noticeFor(access: Access): Promise<HTMLElement> {
    const fixture = TestBed.createComponent(NoticeHost);
    fixture.componentRef.setInput('access', access);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }
});
