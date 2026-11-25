import { computed, signal } from '@angular/core';

export type ColumnBorder = 'needs' | 'charted';

interface Layout {
  needs: number;
  charted: number;
  chartedFolded: boolean;
}

const KEPT_AS = 'edi.console.sitrep-columns';
const NEEDS = { share: 0.36, least: 0.2, most: 0.6 };
const CHARTED = { share: 0.21, least: 0.12, most: 0.4 };
const ONGOING_LEAST = 0.2;

export class ColumnLayout {
  private readonly layout = signal<Layout>(keptLayout());

  readonly needsShare = computed(() => this.layout().needs);
  readonly chartedShare = computed(() => this.layout().charted);
  readonly chartedFolded = computed(() => this.layout().chartedFolded);

  shareAt(border: ColumnBorder): number {
    return border === 'needs' ? this.needsShare() : this.chartedShare();
  }

  resize(border: ColumnBorder, share: number): void {
    const layout = this.layout();
    this.keep(border === 'needs' ? { ...layout, needs: needsWithin(share, layout.charted) } : { ...layout, charted: chartedWithin(share, layout.needs) });
  }

  restore(border: ColumnBorder): void {
    this.keep(border === 'needs' ? { ...this.layout(), needs: NEEDS.share } : { ...this.layout(), charted: CHARTED.share });
  }

  foldCharted(folded: boolean): void {
    this.keep({ ...this.layout(), chartedFolded: folded });
  }

  private keep(layout: Layout): void {
    this.layout.set(layout);
    try {
      localStorage.setItem(KEPT_AS, JSON.stringify(layout));
    } catch {
      return;
    }
  }
}

function keptLayout(): Layout {
  const fallback: Layout = { needs: NEEDS.share, charted: CHARTED.share, chartedFolded: false };
  try {
    const kept = JSON.parse(localStorage.getItem(KEPT_AS) ?? 'null') as Partial<Layout> | null;
    return kept ? readLayout(kept, fallback) : fallback;
  } catch {
    return fallback;
  }
}

function readLayout(kept: Partial<Layout>, fallback: Layout): Layout {
  const charted = typeof kept.charted === 'number' ? within(kept.charted, CHARTED.least, CHARTED.most) : fallback.charted;
  return {
    needs: typeof kept.needs === 'number' ? needsWithin(kept.needs, charted) : fallback.needs,
    charted,
    chartedFolded: kept.chartedFolded === true,
  };
}

function needsWithin(share: number, charted: number): number {
  return within(share, NEEDS.least, Math.min(NEEDS.most, 1 - charted - ONGOING_LEAST));
}

function chartedWithin(share: number, needs: number): number {
  return within(share, CHARTED.least, Math.min(CHARTED.most, 1 - needs - ONGOING_LEAST));
}

function within(share: number, least: number, most: number): number {
  return Math.min(most, Math.max(least, share));
}
