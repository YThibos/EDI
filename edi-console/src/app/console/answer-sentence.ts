import { HttpErrorResponse } from '@angular/common/http';

const SIGNED_OUT = 401;

export function refusalOf(response: HttpErrorResponse): string {
  if (response.status === SIGNED_OUT) {
    return 'Your console session has ended: ask the primary session for the console to sign in again; edi up opens it first.';
  }
  const sentence = typeof response.error === 'string' ? response.error.trim() : '';
  return sentence || 'I did not answer; my core may be restarting, so try again in a moment.';
}
