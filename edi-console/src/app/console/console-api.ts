import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { GivenAnswer, Sitrep } from './sitrep';

const CONSOLE_REQUEST = new HttpHeaders({ 'X-EDI-Console': '1' });

@Injectable({ providedIn: 'root' })
export class ConsoleApi {
  private readonly http = inject(HttpClient);

  sitrep(): Observable<Sitrep> {
    return this.http.get<Sitrep>('/api/sitrep');
  }

  answer(decision: string, answer: GivenAnswer): Observable<string> {
    return this.http.post(`/api/decisions/${decision}/answer`, answer, { headers: CONSOLE_REQUEST, responseType: 'text' });
  }

  askForARecheck(decision: string, note: string): Observable<string> {
    return this.http.post(`/api/decisions/${decision}/recheck`, { note }, { headers: CONSOLE_REQUEST, responseType: 'text' });
  }

  signIn(token: string): Observable<unknown> {
    return this.http.post('/api/console/session', { token }, { headers: CONSOLE_REQUEST, responseType: 'text' });
  }
}
