import { Component, computed, inject, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { AccessNotice } from '../console/access-notice';
import { ChartedList } from '../console/charted-list';
import { ConsoleHeader } from '../console/console-header';
import { DecisionDeck } from '../console/decision-deck';
import { HudStrip } from '../console/hud-strip';
import { LandedList } from '../console/landed-list';
import { OngoingList } from '../console/ongoing-list';
import { PersonalityDesk } from '../console/personality-desk';
import { SitrepReading } from '../console/sitrep-reading';
import { shipMoment } from '../console/stardate';
import { cardsOffTheBoards } from '../console/waiting';
import { WaitingList } from '../console/waiting-list';

@Component({
  selector: 'app-sitrep-page',
  imports: [RouterOutlet, AccessNotice, ConsoleHeader, PersonalityDesk, HudStrip, DecisionDeck, WaitingList, OngoingList, LandedList, ChartedList],
  templateUrl: './sitrep-page.html',
  styleUrl: './sitrep-page.css',
  providers: [SitrepReading],
})
export class SitrepPage {
  protected readonly reading = inject(SitrepReading);
  protected readonly personalityOpen = signal(false);
  protected readonly shipMoment = shipMoment;
  protected readonly waitingOnTheCaptain = computed(() => {
    const sitrep = this.reading.sitrep();
    if (!sitrep) {
      return '';
    }
    const waiting = this.reading.waiting();
    if (waiting) {
      return `${waiting} waiting on you, ${sitrep.address}`;
    }
    return sitrep.workerQuestions.length ? `${questionsOnMe(sitrep.workerQuestions.length)}, ${sitrep.address}` : `all clear, ${sitrep.address}`;
  });
  protected readonly deckCards = computed(() => {
    const sitrep = this.reading.sitrep();
    return sitrep ? cardsOffTheBoards(sitrep) : [];
  });
  protected readonly showsTheDeck = computed(() => {
    const sitrep = this.reading.sitrep();
    return !!sitrep && (this.deckCards().length > 0 || sitrep.answered.length > 0 || (this.reading.waiting() === 0 && sitrep.workerQuestions.length === 0));
  });
  protected readonly chartedShown = computed(() => {
    const sitrep = this.reading.sitrep();
    return sitrep && sitrep.chartedTotal > sitrep.charted.length ? `showing ${sitrep.charted.length} of ${sitrep.chartedTotal}` : '';
  });
}

function questionsOnMe(count: number): string {
  return count === 1 ? '1 question waiting on me' : `${count} questions waiting on me`;
}
