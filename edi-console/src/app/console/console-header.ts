import { Component, computed, input, output } from '@angular/core';

import { PresenceChoice, PresenceShown } from './sitrep';
import { stardate } from './stardate';

@Component({
  selector: 'app-console-header',
  templateUrl: './console-header.html',
  styleUrl: './console-header.css',
})
export class ConsoleHeader {
  readonly generated = input<string | null>(null);
  readonly machine = input<string | null>(null);
  readonly presence = input<PresenceShown | null>(null);
  readonly presenceChosen = output<PresenceChoice>();

  protected readonly stamp = computed(() => stardate(this.generated() ?? ''));
  protected readonly mode = computed(() => {
    const presence = this.presence();
    if (!presence) {
      return null;
    }
    return presence.demo ? 'demo' : presence.mode;
  });
}
