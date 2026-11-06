import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Levels, PersonalityShown, personalityChange } from './personality';
import { GivenAnswer, PresenceChoice, Sitrep } from './sitrep';
import { AnsweredRounds, TaskDetail, TaskPageName } from './task-detail';
import { WorkedWeek } from '../week/worked-week';

const CONSOLE_REQUEST = new HttpHeaders({ 'X-EDI-Console': '1' });

@Injectable({ providedIn: 'root' })
export class ConsoleApi {
  private readonly http = inject(HttpClient);

  sitrep(): Observable<Sitrep> {
    return this.http.get<Sitrep>('/api/sitrep');
  }

  task(task: string): Observable<TaskDetail> {
    return this.http.get<TaskDetail>(`/api/tasks/${encodeURIComponent(task)}`, { headers: { Accept: 'application/json' } });
  }

  week(from: string | null): Observable<WorkedWeek> {
    return this.http.get<WorkedWeek>('/api/week', { params: from ? { from } : {}, headers: { Accept: 'application/json' } });
  }

  taskPage(task: string, page: TaskPageName): Observable<string> {
    return this.http.get(`/api/tasks/${encodeURIComponent(task)}/pages/${page}`, { headers: { Accept: 'text/html' }, responseType: 'text' });
  }

  boardRounds(task: string): Observable<AnsweredRounds> {
    return this.http.get<AnsweredRounds>(`/api/tasks/${encodeURIComponent(task)}/board`, { headers: { Accept: 'application/json' } });
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
