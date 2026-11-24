import { Component, input, signal } from '@angular/core';

import { ColumnBorder, ColumnLayout } from './column-layout';

const KEY_STEP = 0.02;

interface Drag {
  pointer: number;
  startX: number;
  from: number;
  width: number;
}

@Component({
  selector: 'app-column-border',
  template: '<span class="sr-border__line"></span>',
  styleUrl: './column-border.css',
  host: {
    role: 'separator',
    tabindex: '0',
    'aria-orientation': 'vertical',
    '[attr.aria-label]': 'label()',
    '[attr.aria-valuenow]': 'percent()',
    'aria-valuemin': '0',
    'aria-valuemax': '100',
    '[class.sr-border--dragging]': 'drag() !== null',
    '(pointerdown)': 'startDragging($event)',
    '(pointermove)': 'follow($event)',
    '(pointerup)': 'stopDragging($event)',
    '(pointercancel)': 'stopDragging($event)',
    '(dblclick)': 'layout().restore(border())',
    '(keydown)': 'step($event)',
  },
})
export class ColumnBorderHandle {
  readonly border = input.required<ColumnBorder>();
  readonly layout = input.required<ColumnLayout>();
  readonly track = input.required<HTMLElement>();
  readonly label = input.required<string>();

  protected readonly drag = signal<Drag | null>(null);

  protected percent(): number {
    return Math.round(this.layout().shareAt(this.border()) * 100);
  }

  protected startDragging(event: PointerEvent): void {
    if (event.button !== 0) {
      return;
    }
    event.preventDefault();
    (event.currentTarget as HTMLElement).setPointerCapture?.(event.pointerId);
    this.drag.set({ pointer: event.pointerId, startX: event.clientX, from: this.layout().shareAt(this.border()), width: this.track().getBoundingClientRect().width });
  }

  protected follow(event: PointerEvent): void {
    const drag = this.drag();
    if (!drag || drag.pointer !== event.pointerId || drag.width <= 0) {
      return;
    }
    const moved = (event.clientX - drag.startX) / drag.width;
    this.layout().resize(this.border(), drag.from + (this.border() === 'needs' ? moved : -moved));
  }

  protected stopDragging(event: PointerEvent): void {
    if (this.drag()?.pointer === event.pointerId) {
      this.drag.set(null);
    }
  }

  protected step(event: KeyboardEvent): void {
    const widening = this.border() === 'needs' ? 'ArrowRight' : 'ArrowLeft';
    const narrowing = this.border() === 'needs' ? 'ArrowLeft' : 'ArrowRight';
    if (event.key !== widening && event.key !== narrowing) {
      return;
    }
    event.preventDefault();
    const share = this.layout().shareAt(this.border());
    this.layout().resize(this.border(), share + (event.key === widening ? KEY_STEP : -KEY_STEP));
  }
}
