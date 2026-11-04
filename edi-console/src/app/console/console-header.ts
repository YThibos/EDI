import { Component, computed, input, output } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { PresenceChoice, PresenceShown } from './sitrep';
import { shipDay, shipTime } from './stardate';

@Component({
  selector: 'app-console-header',
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './console-header.html',
  styleUrl: './console-header.css',
})
export class ConsoleHeader {
  readonly view = input.required<string>();
  readonly generated = input<string | null>(null);
  readonly zone = input<string | null>(null);
  readonly machine = input<string | null>(null);
  readonly presence = input<PresenceShown | null>(null);
  readonly personalityOpen = input(false);
  readonly waiting = input(0);
  readonly showsPersonality = input(true);
  readonly presenceChosen = output<PresenceChoice>();
  readonly personalityToggled = output<void>();

  protected readonly day = computed(() => shipDay(this.generated() ?? '', this.zone() ?? ''));
  protected readonly time = computed(() => shipTime(this.generated() ?? '-', this.zone() ?? ''));
  protected readonly mode = computed(() => {
    const presence = this.presence();
    if (!presence) {
      return null;
    }
    return presence.demo ? 'demo' : presence.mode;
  });
}
