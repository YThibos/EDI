import { Injectable, InjectionToken, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type FleetStreamEvent = 'connected' | 'dropped' | 'changed';

export const EVENT_SOURCES = new InjectionToken<(url: string) => EventSource>('event sources', {
  providedIn: 'root',
  factory: () => (url: string) => new EventSource(url),
});

@Injectable({ providedIn: 'root' })
export class FleetStream {
  private readonly open = inject(EVENT_SOURCES);

  events(): Observable<FleetStreamEvent> {
    return new Observable<FleetStreamEvent>((subscriber) => {
      const source = this.open('/api/stream');
      source.onopen = () => subscriber.next('connected');
      source.onerror = () => subscriber.next('dropped');
      source.addEventListener('fleet', () => subscriber.next('changed'));
      return () => source.close();
    });
  }
}
