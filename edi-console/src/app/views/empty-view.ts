import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { AccessNotice } from '../console/access-notice';
import { ConsoleHeader } from '../console/console-header';
import { SitrepReading } from '../console/sitrep-reading';

export interface EmptyViewData {
  view: string;
  shows: string;
}

@Component({
  selector: 'app-empty-view',
  imports: [AccessNotice, ConsoleHeader],
  templateUrl: './empty-view.html',
  providers: [SitrepReading],
})
export class EmptyView {
  protected readonly reading = inject(SitrepReading);
  protected readonly data = inject(ActivatedRoute).snapshot.data as EmptyViewData;
}
