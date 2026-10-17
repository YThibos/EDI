import { Component, input } from '@angular/core';

@Component({
  selector: 'app-hud-strip',
  templateUrl: './hud-strip.html',
  styleUrl: './hud-strip.css',
})
export class HudStrip {
  readonly needYou = input.required<number>();
  readonly ongoing = input.required<number>();
  readonly landed = input.required<number>();
  readonly charted = input.required<number>();
}
