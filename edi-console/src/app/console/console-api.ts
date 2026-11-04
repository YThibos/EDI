import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Levels, PersonalityShown, personalityChange } from './personality';
import { GivenAnswer, PresenceChoice, Sitrep } from './sitrep';

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

  choosePresence(mode: PresenceChoice): Observable<string> {
    return this.http.post('/api/presence', { mode }, { headers: CONSOLE_REQUEST, responseType: 'text' });
  }

  goAway(mandate: string): Observable<string> {
    return this.http.post('/api/presence', { mode: 'away', mandate }, { headers: CONSOLE_REQUEST, responseType: 'text' });
  }

  personality(): Observable<PersonalityShown> {
    return this.http.get<PersonalityShown>('/api/personality');
  }

  keepPersonality(levels: Levels): Observable<PersonalityShown> {
    return this.http.post<PersonalityShown>('/api/personality', { change: personalityChange(levels) }, { headers: CONSOLE_REQUEST });
  }

  signIn(token: string): Observable<unknown> {
    return this.http.post('/api/console/session', { token }, { headers: CONSOLE_REQUEST, responseType: 'text' });
  }
}
