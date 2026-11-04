import { HttpErrorResponse } from '@angular/common/http';

export type Access = 'loading' | 'signed-in' | 'signed-out' | 'unreachable';

const SIGNED_OUT = 401;

export function accessRefusedBy(failed: HttpErrorResponse): Access {
  return failed.status === SIGNED_OUT ? 'signed-out' : 'unreachable';
}
