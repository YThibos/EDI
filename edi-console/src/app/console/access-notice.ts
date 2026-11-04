import { Component, input } from '@angular/core';

import { Access } from './access';

@Component({
  selector: 'app-access-notice',
  templateUrl: './access-notice.html',
  host: { style: 'display: contents' },
})
export class AccessNotice {
  readonly access = input.required<Access>();
}
