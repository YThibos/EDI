#!/usr/bin/env node
import { existsSync, mkdirSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { join } from 'node:path';

process.removeAllListeners('warning');
const { DatabaseSync } = await import('node:sqlite');

const USAGE = 'Usage: introspect.mjs [--store PATH] [--since WHEN] [--until WHEN] [--task ID] [--worker ID] [--wake N] [--decision N]'
    + ' [--question-minutes N] [--gate-minutes N] [--max-lines N] [--full] [--drafts DIRECTORY]';
const DEFAULTS = { since: '24h', questionMinutes: 10, gateMinutes: 30, maxLines: 120 };
const OPTIONS = {
    '--store': 'store', '--since': 'since', '--until': 'until', '--task': 'task', '--worker': 'worker', '--wake': 'wake', '--decision': 'decision',
    '--question-minutes': 'questionMinutes', '--gate-minutes': 'gateMinutes', '--max-lines': 'maxLines', '--drafts': 'drafts',
};
const NUMBERS = ['wake', 'decision', 'questionMinutes', 'gateMinutes', 'maxLines'];
const RELATIVE = /^(\d+)([mhd])$/;
const UNIT_MINUTES = { m: 1, h: 60, d: 1440 };
const SECRET = /(gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|AKIA[0-9A-Z]{16}|xox[abpr]-[A-Za-z0-9-]{10,}|sk-[A-Za-z0-9-]{20,}|[Bb]earer\s+\S+|\b[A-Fa-f0-9]{40,}\b|\b[A-Za-z0-9+/_-]{48,}={0,2})/g;
const QUESTION_STATES = ['needs-decision', 'blocked'];
const GATE_KEY = /^nm-/;
const TEXT_WIDTH = 160;
const MINUTE = 60_000;
const TITLES = {
    'acknowledged-without-action': 'A wake the primary did not act on comes back until it is acted on',
    'question-left-open': 'An unanswered worker question comes back until it is answered',
    'gate-parked': 'A pipeline gate parked too long reaches the captain',
    'overlapping-waits': 'A second edi wait in one session is refused',
    'report-while-a-worker-waited': 'A turn report never passes over a waiting worker',
};

class Refusal extends Error {}

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
    run.store ??= join(process.env.EDI_HOME ?? join(homedir(), '.edi'), 'data', 'edi.db');
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

function momentOf(written, before) {
    const relative = RELATIVE.exec(written);
    if (relative) {
        return before - Number(relative[1]) * UNIT_MINUTES[relative[2]] * MINUTE;
    }
    const moment = Date.parse(written);
    if (Number.isNaN(moment)) {
        throw new Refusal(`${written} is neither a moment such as 2026-11-02T14:00:00Z nor a span such as 90m, 6h or 2d.`);
    }
    return moment;
}

function openReadOnly(path) {
    if (!existsSync(path)) {
        throw new Refusal(`No store at ${path}; name it with --store or set EDI_HOME.`);
    }
    return new DatabaseSync(path, { readOnly: true });
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
    const inTheWindow = moment => moment !== null && moment >= run.since && moment <= run.until;
    const concerns = task => !focus.task || task === focus.task;
    const events = store.prepare('select sequence, at, actor, type, task_id, payload from event order by sequence').all()
        .map(row => ({ ...row, at: Date.parse(row.at), payload: JSON.parse(row.payload) }))
        .filter(event => concerns(event.task_id))
        .filter(event => !focus.worker || !event.payload.worker || event.payload.worker === focus.worker);
    const wakes = store.prepare('select number, kind, task_id, worker_id, detail, raised_at, delivered_to, delivered_at, acknowledged_at from wake order by number')
        .all()
        .map(row => ({ ...row, raised_at: Date.parse(row.raised_at), delivered_at: momentOrNull(row.delivered_at), acknowledged_at: momentOrNull(row.acknowledged_at) }))
        .filter(wake => concerns(wake.task_id));
    const streams = store.prepare('select id, session_id, follows, started_at, ended_at from wake_stream order by started_at').all()
        .map(row => ({ ...row, started_at: Date.parse(row.started_at), ended_at: momentOrNull(row.ended_at) }));
    const runs = store.prepare('select run_id, task_id, status, active_step, seen_at, observed_at from pipeline_run').all()
        .map(row => ({ ...row, seen_at: Date.parse(row.seen_at), observed_at: Date.parse(row.observed_at) }))
        .filter(pipeline => concerns(pipeline.task_id));
    const reports = store.prepare('select session_id, reported_at from primary_session where reported_at is not null').all()
        .map(row => ({ session: row.session_id, at: Date.parse(row.reported_at) }));
    const presence = store.prepare('select mode, since, cause from presence').all().map(row => ({ ...row, since: Date.parse(row.since) }));
    return { events, wakes, streams, runs, reports, presence, inTheWindow, now: run.until };
}

function momentOrNull(written) {
    return written === null || written === undefined ? null : Date.parse(written);
}

function timelineOf(facts) {
    const lines = [];
    const add = (at, task, text) => facts.inTheWindow(at) && lines.push({ at, task, text });
    facts.events.forEach(event => add(event.at, event.task_id, eventLine(event)));
    facts.wakes.forEach(wake => {
        add(wake.raised_at, wake.task_id, `wake ${wake.number} ${wake.kind} raised${wake.detail ? ': ' + wake.detail : ''}`);
        add(wake.delivered_at, wake.task_id, `wake ${wake.number} printed to session ${short(wake.delivered_to)}`);
        add(wake.acknowledged_at, wake.task_id, `wake ${wake.number} acknowledged`);
    });
    facts.streams.forEach(stream => {
        add(stream.started_at, '-', `${stream.follows ? 'edi wait --follow' : 'edi wait'} started in session ${short(stream.session_id)}`);
        add(stream.ended_at, '-', `${stream.follows ? 'edi wait --follow' : 'edi wait'} ended in session ${short(stream.session_id)}`);
    });
    facts.runs.forEach(pipeline => add(pipeline.observed_at, pipeline.task_id,
        `pipeline run ${pipeline.run_id} ${pipeline.status}${pipeline.active_step ? ' at ' + pipeline.active_step : ''}`));
    facts.reports.forEach(report => add(report.at, '-', `turn report rendered in session ${short(report.session)}`));
    facts.presence.forEach(presence => add(presence.since, '-', `presence ${presence.mode} (${presence.cause})`));
    return lines.filter(line => line.text !== null).sort((one, other) => one.at - other.at);
}

function eventLine(event) {
    const payload = event.payload;
    const worker = payload.worker ?? event.task_id;
    switch (event.type) {
        case 'StatusReported':
            return `${worker} reports ${payload.state}${payload.key ? ' (' + payload.key + ')' : ''}: ${payload.text}`;
        case 'SteerSent':
            return `steer ${payload.number} sent by ${event.actor}${payload.resolves ? ', resolving ' + payload.resolves : ''}`;
        case 'SteerReceived':
            return `steer ${payload.number} received`;
        case 'SteerAcknowledged':
            return `steer ${payload.number} acknowledged`;
        case 'SteerNotReceived':
            return `steer ${payload.number} not received in time`;
        case 'DecisionRaised':
            return `decision ${payload.number} raised: ${payload.title}`;
        case 'DecisionAnswered':
            return `decision ${payload.number} answered in the ${payload.channel}: ${payload.verbatim}`;
        case 'ToolCallRefused':
            return `the guard refused ${worker}: ${payload.rule}`;
        case 'DeliveryRefused':
            return `the done of ${worker} was refused: ${(payload.gaps ?? []).join('; ')}`;
        case 'TaskDispatched':
            return `${worker} dispatched on ${payload.model}`;
        case 'WorkerDied':
            return `${worker} died: ${payload.cause}`;
        case 'WorkerQuestionUnanswered':
            return `${worker}'s question ${payload.key} raised again`;
        default:
            return `${event.type}${payload.worker ? ' ' + payload.worker : ''} by ${event.actor}`;
    }
}

function flagsIn(facts, run) {
    return [
        ...acknowledgedWithoutAction(facts),
        ...questionsLeftOpen(facts, run.questionMinutes, question => !GATE_KEY.test(question.key), 'question'),
        ...questionsLeftOpen(facts, run.gateMinutes, question => GATE_KEY.test(question.key), 'gate'),
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

function questionsLeftOpen(facts, minutes, belongs, word) {
    return questionsOf(facts).filter(belongs)
        .map(question => ({ ...question, open: (question.answeredAt ?? facts.now) - question.at }))
        .filter(question => question.open > minutes * MINUTE)
        .map(question => ({
            kind: word === 'gate' ? 'gate-parked' : 'question-left-open', at: question.at, task: question.task,
            text: `the ${word} ${question.key} of ${question.worker} waited ${Math.floor(question.open / MINUTE)} minutes`
                + (question.answeredAt ? ' for its resolving steer' : ' and still has no resolving steer')
                + `, past the ${minutes}-minute threshold`,
        }));
}

function questionsOf(facts) {
    const reported = facts.events.filter(event => event.type === 'StatusReported');
    return reported
        .filter(event => QUESTION_STATES.includes(event.payload.state) && event.payload.key)
        .map(event => {
            const resolving = facts.events.find(steer => steer.type === 'SteerSent' && steer.task_id === event.task_id && steer.payload.resolves === event.payload.key
                && steer.at >= event.at);
            const superseded = reported.find(later => later.task_id === event.task_id && later.payload.worker === event.payload.worker && later.at > event.at
                && later.sequence > event.sequence);
            const ends = [resolving?.at, superseded?.at].filter(moment => moment !== undefined);
            return { task: event.task_id, worker: event.payload.worker, key: event.payload.key, at: event.at, answeredAt: ends.length ? Math.min(...ends) : null };
        });
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
        .filter(question => question.at <= report.at && (question.answeredAt ?? facts.now) > report.at)
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
section: Wave 1
blocked-by: -
status: open
---
## Goal

TODO: the hard rule, stated as what the core refuses or raises.

## Context

${capitalised(safe(flag.text, run))}.
The timeline around it, from the store:

${around.map(line => `- ${clock(line.at)} ${line.task}: ${safe(line.text, run)}`).join('\n')}

## Acceptance criteria

- TODO: the refusal or invariant, and the test that proves it.

## Requirements from decisions and answers

- Soft and silent failures are covered by hard rules that are non-negotiable and non-bypassable.

## Read first

- TODO: the code the rule changes.

## How to verify

- \`./mvnw verify\`.
`;
}

function safe(text, run) {
    const redacted = String(text).replace(SECRET, '[redacted]').replace(/\s+/g, ' ');
    return run.full || redacted.length <= TEXT_WIDTH ? redacted : redacted.slice(0, TEXT_WIDTH - 3) + '...';
}

function capitalised(text) {
    return text.charAt(0).toUpperCase() + text.slice(1);
}

function short(session) {
    return session ? String(session).slice(0, 8) : '-';
}

function clock(moment) {
    return iso(moment).slice(0, 16).replace('T', ' ');
}

function iso(moment) {
    return new Date(moment).toISOString().replace('.000Z', 'Z');
}
