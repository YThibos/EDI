import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EffortTable } from './effort-table';
import { YieldAtAnEffort } from './worked-week';

const NO_TOKENS = { input: 0, cacheRead: 0, cacheWrite: 0, output: 0 };

const EFFORTS: YieldAtAnEffort[] = [
  {
    effort: 'high',
    tasks: 2,
    weightedWorkerTokens: 3_000_000,
    weightedWorkerTokensPerTask: 1_500_000,
    weightedPipelineTokens: 500_000,
    workerTokens: { input: 1_500_000, cacheRead: 10_000_000, cacheWrite: 100_000, output: 75_000 },
    pipelineTokens: { input: 200_000, cacheRead: 2_000_000, cacheWrite: 0, output: 20_000 },
    reviewRounds: 6,
    findings: 7,
    findingsPerReviewRound: 7 / 6,
  },
  {
    effort: 'xhigh',
    tasks: 2,
    weightedWorkerTokens: 5_600_000,
    weightedWorkerTokensPerTask: 2_800_000,
    weightedPipelineTokens: 0,
    workerTokens: { input: 2_100_000, cacheRead: 20_000_000, cacheWrite: 400_000, output: 200_000 },
    pipelineTokens: NO_TOKENS,
    reviewRounds: 1,
    findings: 0,
    findingsPerReviewRound: 0,
  },
];

describe('EffortTable', () => {
  let fixture: ComponentFixture<EffortTable>;

  beforeEach(() => {
    fixture = TestBed.createComponent(EffortTable);
  });

  it('shows each effort\'s weighted tokens and findings per review round', async () => {
    const table = await shown(EFFORTS);

    expect(texts(table, 'thead th')).toEqual(['Effort', 'Tasks', 'Worker tokens', 'Per task', 'Pipeline tokens', 'Review rounds', 'Findings', 'Per round']);
    expect(rows(table)).toEqual([
      ['high', '2', '3.0M', '1.5M', '500k', '6', '7', '1.17'],
      ['xhigh', '2', '5.6M', '2.8M', '0', '1', '0', '0.00'],
    ]);
  });

  it('names a worker launched without an effort flag and shows no yield without a review round', async () => {
    const table = await shown([{ ...EFFORTS[0], effort: null, reviewRounds: 0, findings: 0, findingsPerReviewRound: null }]);

    expect(rows(table)).toEqual([['no flag', '2', '3.0M', '1.5M', '500k', '0', '0', '-']]);
  });

  it('says so when no task\'s cost was counted in the week', async () => {
    const table = await shown([]);

    expect(table.querySelector('table')).toBeNull();
    expect(table.textContent?.trim()).toBe('No task\'s cost was counted this week.');
  });

  async function shown(efforts: YieldAtAnEffort[]): Promise<HTMLElement> {
    fixture.componentRef.setInput('efforts', efforts);
    await fixture.whenStable();
    return fixture.nativeElement;
  }
});

function rows(table: HTMLElement): string[][] {
  return Array.from(table.querySelectorAll('tbody tr')).map((row) => texts(row as HTMLElement, ':scope > *'));
}

function texts(element: HTMLElement, selector: string): string[] {
  return Array.from(element.querySelectorAll(selector)).map((cell) => cell.textContent?.trim() ?? '');
}
