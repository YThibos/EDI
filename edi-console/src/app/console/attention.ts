import { Injectable, inject } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { Router, TitleStrategy } from '@angular/router';

import { Sitrep } from './sitrep';

const ASKED = 'edi.console.notifications-asked';

interface News {
  id: string;
  text: string;
}

@Injectable({ providedIn: 'root' })
export class Attention {
  private readonly title = inject(Title);
  private readonly router = inject(Router);
  private readonly titles = inject(TitleStrategy);
  private seen: Set<string> | null = null;
  private asked = false;

  follow(sitrep: Sitrep, waiting: number): void {
    this.title.setTitle(countedOnto(this.viewOnScreen(), waiting));
    this.announceNews(sitrep);
  }

  askOnce(): void {
    if (this.asked || typeof Notification === 'undefined' || Notification.permission !== 'default' || alreadyAsked()) {
      return;
    }
    this.asked = true;
    rememberAsking();
    Promise.resolve(Notification.requestPermission()).catch(() => undefined);
  }

  private viewOnScreen(): string {
    return this.titles.buildTitle(this.router.routerState.snapshot) ?? '';
  }

  private announceNews(sitrep: Sitrep): void {
    const news = newsIn(sitrep);
    const seen = this.seen;
    if (seen) {
      news.filter((item) => !seen.has(item.id)).forEach(announce);
    }
    this.seen = new Set(news.map((item) => item.id));
  }
}

function countedOnto(view: string, waiting: number): string {
  return waiting > 0 ? `(${waiting}) ${view}` : view;
}

function newsIn(sitrep: Sitrep): News[] {
  return [
    ...sitrep.decisions.map((card) => ({ id: `decision:${card.number}`, text: `I need your decision on ${card.number}: ${card.title}` })),
    ...sitrep.reviews.map((review) => ({ id: `review:${review.url}`, text: `I have ${review.title} ready for your review.` })),
  ];
}

function announce(item: News): void {
  if (typeof Notification === 'undefined' || Notification.permission !== 'granted') {
    return;
  }
  try {
    new Notification('EDI', { body: item.text, tag: item.id });
  } catch {
    return;
  }
}

function alreadyAsked(): boolean {
  try {
    return localStorage.getItem(ASKED) !== null;
  } catch {
    return false;
  }
}

function rememberAsking(): void {
  try {
    localStorage.setItem(ASKED, 'yes');
  } catch {
    return;
  }
}
