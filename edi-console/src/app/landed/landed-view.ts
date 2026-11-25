import { Component, inject } from '@angular/core';

import { AccessNotice } from '../console/access-notice';
import { ConsoleHeader } from '../console/console-header';
import { LandedList } from '../console/landed-list';
import { SitrepReading } from '../console/sitrep-reading';

@Component({
  selector: 'app-landed-view',
  imports: [AccessNotice, ConsoleHeader, LandedList],
  templateUrl: './landed-view.html',
  providers: [SitrepReading],
})
export class LandedView {
  protected readonly reading = inject(SitrepReading);
}
