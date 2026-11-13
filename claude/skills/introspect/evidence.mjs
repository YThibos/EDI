import { existsSync } from 'node:fs';
import { homedir } from 'node:os';
import { join } from 'node:path';

process.removeAllListeners('warning');
const { DatabaseSync } = await import('node:sqlite');

export const MINUTE = 60_000;
export const GATE_KEY = /^nm-/;

const RELATIVE = /^(\d+)([mhd])$/;
const UNIT_MINUTES = { m: 1, h: 60, d: 1440 };
const SECRET = /(gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|AKIA[0-9A-Z]{16}|xox[abpr]-[A-Za-z0-9-]{10,}|sk-[A-Za-z0-9-]{20,}|[Bb]earer\s+\S+|\b[A-Fa-f0-9]{40,}\b|\b[A-Za-z0-9+/_-]{48,}={0,2})/g;
const QUESTION_STATES = ['needs-decision', 'blocked'];
const TEXT_WIDTH = 160;

export class Refusal extends Error {}

export function ediHome() {
    return process.env.EDI_HOME ?? join(homedir(), '.edi');
}

export function momentOf(written, before) {
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

export function openReadOnly(path) {
    if (!existsSync(path)) {
        throw new Refusal(`No store at ${path}; name it with --store or set EDI_HOME.`);
    }
    return new DatabaseSync(path, { readOnly: true });
}

export function storeFacts(store, window) {
    const events = store.prepare('select sequence, at, actor, type, task_id, payload from event order by sequence').all()
        .map(row => ({ ...row, at: Date.parse(row.at), payload: JSON.parse(row.payload) }));
    const wakes = store.prepare('select number, kind, task_id, worker_id, detail, raised_at, delivered_to, delivered_at, acknowledged_at from wake order by number')
        .all()
        .map(row => ({ ...row, raised_at: Date.parse(row.raised_at), delivered_at: momentOrNull(row.delivered_at), acknowledged_at: momentOrNull(row.acknowledged_at) }));
    const streams = store.prepare('select id, session_id, follows, started_at, ended_at from wake_stream order by started_at').all()
        .map(row => ({ ...row, started_at: Date.parse(row.started_at), ended_at: momentOrNull(row.ended_at) }));
    const runs = store.prepare('select run_id, task_id, status, active_step, seen_at, observed_at from pipeline_run').all()
        .map(row => ({ ...row, seen_at: Date.parse(row.seen_at), observed_at: Date.parse(row.observed_at) }));
    const reports = store.prepare('select session_id, reported_at from primary_session where reported_at is not null').all()
        .map(row => ({ session: row.session_id, at: Date.parse(row.reported_at) }));
    const presence = store.prepare('select mode, since, cause from presence').all().map(row => ({ ...row, since: Date.parse(row.since) }));
    const inTheWindow = moment => moment !== null && moment >= window.since && moment <= window.until;
    return { events, wakes, streams, runs, reports, presence, inTheWindow, now: window.until };
}

function momentOrNull(written) {
    return written === null || written === undefined ? null : Date.parse(written);
}

export function timelineOf(facts) {
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

export function questionsLeftOpen(facts, minutes, word) {
    const belongs = word === 'gate' ? question => GATE_KEY.test(question.key) : question => !GATE_KEY.test(question.key);
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

export function questionsOf(facts) {
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

export function safe(text, run) {
    const redacted = String(text).replace(SECRET, '[redacted]').replace(/\s+/g, ' ');
    return run.full || redacted.length <= TEXT_WIDTH ? redacted : redacted.slice(0, TEXT_WIDTH - 3) + '...';
}

export function fenced(text) {
    return text.replace(/`{3,}/g, match => "'".repeat(match.length));
}

export function capitalised(text) {
    return text.charAt(0).toUpperCase() + text.slice(1);
}

export function short(session) {
    return session ? String(session).slice(0, 8) : '-';
}

export function clock(moment) {
    return iso(moment).slice(0, 16).replace('T', ' ');
}

export function iso(moment) {
    return new Date(moment).toISOString().replace('.000Z', 'Z');
}
