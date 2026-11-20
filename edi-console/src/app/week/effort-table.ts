import { Component, input } from '@angular/core';

import { YieldAtAnEffort, effortName, findingsPerRound, weightedTokens } from './worked-week';

@Component({
  selector: 'app-effort-table',
  templateUrl: './effort-table.html',
  styleUrl: './effort-table.css',
})
export class EffortTable {
  readonly efforts = input.required<YieldAtAnEffort[]>();

  protected readonly effortName = effortName;
  protected readonly weightedTokens = weightedTokens;
  protected readonly findingsPerRound = findingsPerRound;
}
