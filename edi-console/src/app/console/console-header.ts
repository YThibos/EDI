import { Component, computed, input } from '@angular/core';

import { stardate } from './stardate';

@Component({
  selector: 'app-console-header',
  templateUrl: './console-header.html',
  styleUrl: './console-header.css',
})
export class ConsoleHeader {
  readonly generated = input<string | null>(null);
  readonly machine = input<string | null>(null);

  protected readonly stamp = computed(() => stardate(this.generated() ?? ''));
}
