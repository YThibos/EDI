import { HttpErrorResponse } from '@angular/common/http';

const SIGNED_OUT = 401;

export function refusalOf(response: HttpErrorResponse): string {
  if (response.status === SIGNED_OUT) {
    return signedOutSentence();
  }
  return stated(response) || 'I did not answer; my core may be restarting, so try again in a moment.';
}

export function notStoredBecause(response: HttpErrorResponse): string {
  const reason = response.status === SIGNED_OUT ? signedOutSentence() : stated(response) || 'My core did not answer, so try again in a moment.';
  return `Not stored. ${reason}`;
}

function signedOutSentence(): string {
  return 'Your console session has ended, or a newer sign-in replaced it: run edi console in the primary session to sign in again.';
}

function stated(response: HttpErrorResponse): string {
  return typeof response.error === 'string' ? response.error.trim() : '';
}
