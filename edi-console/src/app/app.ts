import { Component } from '@angular/core';

@Component({
  selector: 'app-root',
  template: `
    <main>
      <h1>EDI</h1>
      <p>Enhanced Development Intelligence</p>
    </main>
  `,
  styles: `
    main {
      padding: 48px 16px;
      text-align: center;
    }

    h1 {
      margin: 0;
      font-size: 48px;
      letter-spacing: 0.2em;
    }

    p {
      margin: 8px 0 0;
      color: var(--text-dim);
    }
  `,
})
export class App {}
