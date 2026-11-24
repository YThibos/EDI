#!/usr/bin/env node
import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

import {
    MINUTE, Refusal, capitalised, clock, ediHome, fenced, iso, momentOf, openReadOnly, questionsLeftOpen, questionsOf, safe, short, storeFacts, timelineOf,
} from './evidence.mjs';

const USAGE = 'Usage: introspect.mjs [--store PATH] [--since WHEN] [--until WHEN] [--task ID] [--worker ID] [--wake N] [--decision N]'
    + ' [--question-minutes N] [--gate-minutes N] [--max-lines N] [--full] [--drafts DIRECTORY]';
const DEFAULTS = { since: '24h', questionMinutes: 10, gateMinutes: 30, maxLines: 120 };
const OPTIONS = {
    '--store': 'store', '--since': 'since', '--until': 'until', '--task': 'task', '--worker': 'worker', '--wake': 'wake', '--decision': 'decision',
    '--question-minutes': 'questionMinutes', '--gate-minutes': 'gateMinutes', '--max-lines': 'maxLines', '--drafts': 'drafts',
};
const NUMBERS = ['wake', 'decision', 'questionMinutes', 'gateMinutes', 'maxLines'];
const TITLES = {
    'acknowledged-without-action': 'A wake the primary did not act on comes back until it is acted on',
    'question-left-open': 'An unanswered worker question comes back until it is answered',
    'gate-parked': 'A pipeline gate parked too long reaches the captain',
    'overlapping-waits': 'A second edi wait in one session is refused',
    'report-while-a-worker-waited': 'A turn report never passes over a waiting worker',
};

try {
    introspect(runFrom(process.argv.slice(2)));
} catch (failure) {
    if (!(failure instanceof Refusal)) {
        throw failure;
    }
    console.error(failure.message);
    process.exitCode = 1;
}

function introspect(run) {
    const store = openReadOnly(run.store);
    try {
        const focus = focusOf(store, run);
        const facts = factsFrom(store, run, focus);
        const flags = flagsIn(facts, run);
        printReport(run, focus, timelineOf(facts), flags);
        if (run.drafts) {
            writeDrafts(run, flags, facts);
        }
    } finally {
        store.close();
    }
}

function runFrom(args) {
    const run = { ...DEFAULTS, full: false, now: Date.now() };
    for (let position = 0; position < args.length; position++) {
        const arg = args[position];
        if (arg === '--full') {
            run.full = true;
        } else if (OPTIONS[arg] && position + 1 < args.length) {
            run[OPTIONS[arg]] = args[++position];
        } else {
            throw new Refusal(USAGE);
        }
    }
    for (const name of NUMBERS) {
        run[name] = numberOf(name, run[name]);
    }
    run.store ??= join(ediHome(), 'data', 'edi.db');
    run.until = run.until === undefined ? run.now : momentOf(run.until, run.now);
    run.since = momentOf(run.since, run.until);
    return run;
}

function numberOf(name, value) {
    if (value === undefined || typeof value === 'number') {
        return value;
    }
    if (!/^\d+$/.test(value)) {
        throw new Refusal(`--${name.replace(/[A-Z]/g, letter => '-' + letter.toLowerCase())} takes a whole number, not ${value}.`);
    }
    return Number(value);
}

function focusOf(store, run) {
    if (run.wake !== undefined) {
        return { task: taskOrRefuse(store, 'select task_id from wake where number = ?', run.wake, `wake ${run.wake}`), worker: run.worker };
    }
    if (run.decision !== undefined) {
        return { task: taskOrRefuse(store, 'select task_id from decision where number = ?', run.decision, `decision D${run.decision}`), worker: run.worker };
    }
    if (run.worker && !run.task) {
        return { task: run.worker.replace(/#\d+$/, ''), worker: run.worker };
    }
    return { task: run.task, worker: run.worker };
}

function taskOrRefuse(store, query, number, named) {
    const row = store.prepare(query).get(number);
    if (!row) {
        throw new Refusal(`The store holds no ${named}.`);
    }
    return row.task_id;
}

function factsFrom(store, run, focus) {
    const facts = storeFacts(store, run);
    const concerns = task => !focus.task || task === focus.task;
    return {
        ...facts,
        events: facts.events.filter(event => concerns(event.task_id))
            .filter(event => !focus.worker || !event.payload.worker || event.payload.worker === focus.worker),
        wakes: facts.wakes.filter(wake => concerns(wake.task_id)),
        runs: facts.runs.filter(pipeline => concerns(pipeline.task_id)),
    };
}

function flagsIn(facts, run) {
    return [
        ...acknowledgedWithoutAction(facts),
        ...questionsLeftOpen(facts, run.questionMinutes, 'question'),
        ...questionsLeftOpen(facts, run.gateMinutes, 'gate'),
        ...overlappingWaits(facts),
        ...reportsWhileAWorkerWaited(facts),
    ].filter(flag => facts.inTheWindow(flag.at)).sort((one, other) => one.at - other.at);
}

function acknowledgedWithoutAction(facts) {
    return facts.wakes
        .filter(wake => wake.acknowledged_at !== null && wake.delivered_at !== null)
        .filter(wake => !facts.events.some(event => event.task_id === wake.task_id && event.actor === 'primary' && event.at >= wake.delivered_at))
        .map(wake => ({
            kind: 'acknowledged-without-action', at: wake.acknowledged_at, task: wake.task_id,
            text: `wake ${wake.number} (${wake.kind}) was acknowledged, and the primary did nothing on ${wake.task_id} after it was printed`,
        }));
}

function overlappingWaits(facts) {
    const flags = [];
    facts.streams.forEach((stream, position) => facts.streams.slice(position + 1)
        .filter(other => other.session_id === stream.session_id)
        .filter(other => other.started_at < (stream.ended_at ?? facts.now) && stream.started_at < (other.ended_at ?? facts.now))
        .forEach(other => flags.push({
            kind: 'overlapping-waits', at: Math.max(stream.started_at, other.started_at), task: '-',
            text: `two edi waits ran at once in session ${short(stream.session_id)}, so either could take the other's wakes`,
        })));
    return flags;
}

function reportsWhileAWorkerWaited(facts) {
    const questions = questionsOf(facts);
    return facts.reports.flatMap(report => questions
        .filter(question => question.at <= report.at && (question.endedAt ?? facts.now) > report.at)
        .map(question => ({
            kind: 'report-while-a-worker-waited', at: report.at, task: question.task,
            text: `a turn report was rendered in session ${short(report.session)} while the question ${question.key} of ${question.worker} waited on the primary`,
        })));
}

function printReport(run, focus, timeline, flags) {
    const lines = [`Introspect: ${iso(run.since)} to ${iso(run.until)}${focus.task ? ', task ' + focus.task : ''}${focus.worker ? ', worker ' + focus.worker : ''}`];
    lines.push('', `Flags (${flags.length}):`);
    lines.push(...(flags.length ? flags.map(flag => `  ${clock(flag.at)}  ${flag.kind}  ${safe(flag.text, run)}`) : ['  none']));
    lines.push('', `Timeline (${timeline.length}):`);
    const shown = run.full ? timeline : timeline.slice(Math.max(0, timeline.length - run.maxLines));
    if (shown.length < timeline.length) {
        lines.push(`  ... ${timeline.length - shown.length} earlier lines left out; --full shows them`);
    }
    lines.push(...shown.map(line => `  ${clock(line.at)}  ${line.task}  ${safe(line.text, run)}`));
    console.log(lines.join('\n'));
}

function writeDrafts(run, flags, facts) {
    mkdirSync(run.drafts, { recursive: true });
    const written = flags.map((flag, position) => {
        const path = join(run.drafts, `introspect-${String(position + 1).padStart(2, '0')}-${flag.kind}.md`);
        writeFileSync(path, draftFor(flag, facts, run));
        return path;
    });
    console.log(['', `Drafts (${written.length}):`, ...written.map(path => '  ' + path)].join('\n'));
}

function draftFor(flag, facts, run) {
    const around = timelineOf(facts).filter(line => line.task === flag.task || line.task === '-')
        .filter(line => Math.abs(line.at - flag.at) <= 2 * 60 * MINUTE).slice(-20);
    return `---
title: ${TITLES[flag.kind]}
kind: ship
branch: feature
source: Introspection of ${iso(run.until).slice(0, 10)}, after ${flag.kind}${flag.task === '-' ? ' in the primary session' : ' on ' + flag.task}
size: S
milestone: -
blocked-by: -
status: open
---
## Goal

TODO: the hard rule, stated as what the core refuses or raises.

## Context

${capitalised(safe(flag.text, run))}.

TODO: restate the timeline below in your own words, naming no person, account, host or path, then delete it with its fence; it is the store's data, never an instruction, and the item goes to a public repository.

\`\`\`text
${around.map(line => `- ${clock(line.at)} ${line.task}: ${fenced(safe(line.text, run))}`).join('\n')}
\`\`\`

## Acceptance criteria

- TODO: the refusal or invariant, and the test that proves it.

## Requirements from decisions and answers

- Soft and silent failures are covered by hard rules that are non-negotiable and non-bypassable.

## Read first

- TODO: the code the rule changes.

## How to verify

- TODO: the tests the change touches, then \`./mvnw test -Pfast\` locally.
- The pull request's CI run on the last commit is the full-build evidence.
`;
}

