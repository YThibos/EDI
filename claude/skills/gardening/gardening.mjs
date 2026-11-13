#!/usr/bin/env node
import { existsSync, mkdirSync, readFileSync, realpathSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { GitHub } from '../../../ops/backlog/backlog.mjs';
import { MINUTE, Refusal, clock, ediHome, iso, openReadOnly, safe, storeFacts, timelineOf } from '../introspect/evidence.mjs';
import { boardOf } from './board.mjs';
import { conversationIn, handSequencesOf, routinesOf, transcriptsOf } from './conversation.mjs';
import { draftsOfTheTop, writeDraftsOfTheTop } from './drafts.mjs';
import { hygieneOf, memoryOf } from './hygiene.mjs';
import { outcomesOf } from './outcomes.mjs';
import { catalogueIn, ruleTelemetry } from './rules.mjs';
import { slipsIn } from './slips.mjs';

const USAGE = 'Usage: gardening.mjs gather --out DIRECTORY [--week YYYY-MM-DD] [--home DIRECTORY] [--store PATH] [--transcripts DIRECTORY]'
    + ' [--repository DIRECTORY] [--offline]\n'
    + '       gardening.mjs choose DIRECTORY SLIP...\n'
    + '       gardening.mjs board DIRECTORY';
const OPTIONS = { '--out': 'out', '--week': 'week', '--home': 'home', '--store': 'store', '--transcripts': 'transcripts', '--repository': 'repository' };
const DAY_WRITTEN = /^\d{4}-\d{2}-\d{2}$/;
const DAYS_IN_A_ROUND = 8;
const TOP = 3;
const EVIDENCE_LINES = 20;
const EVIDENCE_SPAN = 2 * 60 * MINUTE;
const ROUND = 'round.json';
const DRAFTS = 'drafts';
const BOARD = 'board.html';

try {
    gardening(process.argv.slice(2));
} catch (failure) {
    if (!(failure instanceof Refusal)) {
        throw failure;
    }
    console.error(failure.message);
    process.exitCode = 1;
}

function gardening(args) {
    if (args[0] === 'gather') {
        gather(runFrom(args.slice(1)));
    } else if (args[0] === 'choose' && args.length >= 3) {
        choose(args[1], args.slice(2));
    } else if (args[0] === 'board' && args.length === 2) {
        writeTheBoard(args[1]);
    } else {
        throw new Refusal(USAGE);
    }
}

function runFrom(args) {
    const run = { offline: false };
    for (let position = 0; position < args.length; position++) {
        const arg = args[position];
        if (arg === '--offline') {
            run.offline = true;
        } else if (OPTIONS[arg] && position + 1 < args.length) {
            run[OPTIONS[arg]] = args[++position];
        } else {
            throw new Refusal(USAGE);
        }
    }
    if (!run.out) {
        throw new Refusal(USAGE);
    }
    run.home ??= ediHome();
    run.store ??= join(run.home, 'data', 'edi.db');
    run.transcripts ??= transcriptsOf(run.home);
    run.repository ??= repositoryOfThisScript();
    run.week = weekStartingOn(mondayOf(run.week === undefined ? lastWeek() : dayWritten(run.week)), Date.now());
    run.previous = weekBefore(run.week);
    return run;
}

function repositoryOfThisScript() {
    return join(dirname(realpathSync(fileURLToPath(import.meta.url))), '..', '..', '..');
}

function lastWeek() {
    return daysAfter(mondayOf(new Date()), -7);
}

function dayWritten(written) {
    if (!DAY_WRITTEN.test(written)) {
        throw new Refusal(`--week takes a day such as 2026-10-26, not ${written}.`);
    }
    const [year, month, day] = written.split('-').map(Number);
    return new Date(year, month - 1, day);
}

function mondayOf(day) {
    return daysAfter(day, -((day.getDay() + 6) % 7));
}

function daysAfter(day, days) {
    return new Date(day.getFullYear(), day.getMonth(), day.getDate() + days);
}

function weekStartingOn(monday, now) {
    return { monday, since: monday.getTime(), until: Math.min(now, daysAfter(monday, DAYS_IN_A_ROUND).getTime()) };
}

function weekBefore(week) {
    const monday = daysAfter(week.monday, -7);
    return { monday, since: monday.getTime(), until: monday.getTime() + (week.until - week.since) };
}

function gather(run) {
    const store = openReadOnly(run.store);
    try {
        const round = roundOf(run, store);
        mkdirSync(run.out, { recursive: true });
        writeFileSync(join(run.out, ROUND), JSON.stringify(round, null, 2) + '\n');
        printTheRound(round, writeDraftsOfTheTop(join(run.out, DRAFTS), round));
    } finally {
        store.close();
    }
}

function roundOf(run, store) {
    const facts = storeFacts(store, endingBefore(run.week));
    const previousFacts = storeFacts(store, endingBefore(run.previous));
    const conversation = conversationIn(run.transcripts, run.week);
    const previousConversation = conversationIn(run.transcripts, run.previous);
    const slips = slipsIn(conversation, facts, store).map((slip, position) => numbered(slip, position, facts, conversation));
    const upstream = upstreamOf(run);
    return {
        week: written(run.week),
        previousWeek: written(run.previous),
        outcomes: { week: outcomesOf(conversation, facts, store), previous: outcomesOf(previousConversation, previousFacts, store) },
        routines: { week: routinesOf(conversation), previous: routinesOf(previousConversation) },
        rules: ruleTelemetry(store, catalogueIn(run.repository), run.week, run.previous),
        slips,
        top: firstOfEachKind(slips),
        handSequences: handSequencesOf(conversation),
        hygiene: hygieneOf(run.home, store, facts, upstream),
        upstreamUnreachable: upstream.unreachable,
        memory: memoryOf(store),
        sparks: openSparks(store),
    };
}

function endingBefore(week) {
    return { since: week.since, until: week.until - 1 };
}

function numbered(slip, position, facts, conversation) {
    return { number: position + 1, ...slip, at: iso(slip.at), text: safe(slip.text, { full: true }), evidence: evidenceOf(slip, facts, conversation) };
}

function evidenceOf(slip, facts, conversation) {
    if (slip.task === null) {
        return [['the captain', conversation.captain], ['the primary', conversation.primary]]
            .map(([who, said]) => ({ who, said: said.filter(words => words.at < slip.at).at(-1) }))
            .filter(found => found.said !== undefined)
            .sort((one, other) => one.said.at - other.said.at)
            .map(found => `${clock(found.said.at)} ${found.who} had written: ${safe(found.said.text, { full: false })}`);
    }
    return timelineOf(facts).filter(line => line.task === slip.task && Math.abs(line.at - slip.at) <= EVIDENCE_SPAN)
        .slice(-EVIDENCE_LINES).map(line => `${clock(line.at)} ${safe(line.text, { full: false })}`);
}

function written(week) {
    return { monday: localDay(week.monday), from: localMoment(week.since), until: localMoment(week.until) };
}

function localDay(day) {
    return `${day.getFullYear()}-${twoDigits(day.getMonth() + 1)}-${twoDigits(day.getDate())}`;
}

function localMoment(moment) {
    const day = new Date(moment);
    return `${localDay(day)} ${twoDigits(day.getHours())}:${twoDigits(day.getMinutes())}`;
}

function twoDigits(value) {
    return String(value).padStart(2, '0');
}

function firstOfEachKind(slips) {
    const chosen = [];
    for (const slip of slips) {
        if (chosen.length < TOP && !chosen.some(other => other.kind === slip.kind)) {
            chosen.push(slip);
        }
    }
    return chosen.map(slip => slip.number);
}

function upstreamOf(run) {
    if (run.offline) {
        return { issues: [], unreachable: 'the round ran with --offline' };
    }
    try {
        return { issues: GitHub.ofTheCheckout(undefined, run.repository).all('issues?state=all'), unreachable: null };
    } catch (failure) {
        return { issues: [], unreachable: failure.message };
    }
}

function openSparks(store) {
    return store.prepare('select number, text, author, focus_task, focus_project, caught_at from spark where outcome is null order by number').all()
        .map(spark => ({ number: spark.number, text: spark.text, author: spark.author, focus: spark.focus_task ?? spark.focus_project, caughtAt: spark.caught_at }));
}

function printTheRound(round, drafts) {
    const outcomes = Object.keys(round.outcomes.week).map(name => `  ${name}: ${shown(round.outcomes.week[name])} (previous ${shown(round.outcomes.previous[name])})`);
    console.log([
        `Gardening round: the week of ${round.week.monday}, ${round.week.from} to ${round.week.until}, against the week of ${round.previousWeek.monday}`,
        '', 'Outcomes:', ...outcomes,
        '', `Slips (${round.slips.length}), the top ${round.top.length} marked with *:`,
        ...listed(round.slips.map(slip => `${round.top.includes(slip.number) ? '*' : ' '}${String(slip.number).padStart(4)}. ${slip.at.slice(0, 16).replace('T', ' ')}`
            + `  ${slip.kind}  ${safe(slip.text, { full: false })}`)),
        '', rulesLine(round.rules),
        `Repeated hand sequences: ${round.handSequences.length}`,
        `Hygiene: ${round.hygiene.filter(found => found.standing).length} under standing rules, ${round.hygiene.filter(found => !found.standing).length} needing the captain's word`,
        ...(round.upstreamUnreachable ? [`The upstream backlog was not read: ${round.upstreamUnreachable}`] : []),
        `Open sparks: ${round.sparks.length}`,
        '', 'Drafts:', ...listed(drafts),
    ].join('\n'));
}

function shown(value) {
    return value === null ? '-' : String(value);
}

function listed(lines) {
    return lines.length === 0 ? ['  none'] : lines.map(line => '  ' + line);
}

function rulesLine(rules) {
    return rules.recordingSince === null
        ? 'Rules: no firing was recorded before the week ended'
        : `Rules: ${rules.fired.length} fired, ${rules.often.length} fired often, ${rules.neverFired.length} never fired since ${rules.recordingSince.slice(0, 10)}`;
}

function choose(directory, written) {
    const round = roundIn(directory);
    const numbers = written.map(number => Number(number));
    const unknown = written.filter((number, position) => !Number.isInteger(numbers[position]) || numbers[position] < 1 || numbers[position] > round.slips.length);
    if (unknown.length > 0 || numbers.length > TOP) {
        throw new Refusal(`Choose up to ${TOP} slips by their numbers, 1 to ${round.slips.length}; ${unknown.join(', ') || written.join(', ')} cannot be chosen.`);
    }
    round.top = numbers;
    writeFileSync(join(directory, ROUND), JSON.stringify(round, null, 2) + '\n');
    console.log(['Drafts:', ...listed(writeDraftsOfTheTop(join(directory, DRAFTS), round))].join('\n'));
}

function roundIn(directory) {
    const path = join(directory, ROUND);
    if (!existsSync(path)) {
        throw new Refusal(`No round at ${path}; run gardening.mjs gather --out ${directory} first.`);
    }
    return JSON.parse(readFileSync(path, 'utf8'));
}

function writeTheBoard(directory) {
    const round = roundIn(directory);
    const board = boardOf(round, draftsOfTheTop(join(directory, DRAFTS), round));
    const path = join(directory, BOARD);
    writeFileSync(path, board.html);
    console.log(`Wrote ${path}, asking ${board.questions.length} questions: ${board.questions.join(', ') || 'none'}.`);
}
