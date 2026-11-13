import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

import { iso } from '../introspect/evidence.mjs';

const CATALOGUE_ROW = /^\| `([a-z]+\.[a-z0-9-]+)` \| [^|]* \| (.+) \|$/;
const OUTCOMES = ['refused', 'warned', 'allowed'];

const OFTEN = 10;

export function catalogueIn(repository) {
    const path = join(repository, 'docs', 'rules.md');
    if (!existsSync(path)) {
        return [];
    }
    return readFileSync(path, 'utf8').split('\n').map(line => CATALOGUE_ROW.exec(line)).filter(row => row !== null)
        .map(row => ({ rule: row[1], protects: row[2].trim() }));
}

export function ruleTelemetry(store, catalogue, week, previous) {
    const firings = store.prepare('select at, rule, outcome from rule_fired order by number').all().map(row => ({ ...row, at: Date.parse(row.at) }));
    const protects = new Map(catalogue.map(entry => [entry.rule, entry.protects]));
    const inWeek = firings.filter(firing => within(firing.at, week));
    const fired = [...new Set(inWeek.map(firing => firing.rule))].map(rule => firedRule(rule, protects, inWeek, firings, previous))
        .sort((one, other) => other.total - one.total || one.rule.localeCompare(other.rule));
    const recorded = firings.filter(firing => firing.at < week.until);
    const everFired = new Set(recorded.map(firing => firing.rule));
    return {
        recordingSince: recorded.length === 0 ? null : iso(recorded[0].at),
        fired,
        often: fired.filter(rule => rule.total >= OFTEN).map(rule => rule.rule),
        neverFired: recorded.length === 0 ? [] : catalogue.filter(entry => !everFired.has(entry.rule)),
    };
}

function firedRule(rule, protects, inWeek, firings, previous) {
    const own = inWeek.filter(firing => firing.rule === rule);
    return {
        rule,
        protects: protects.get(rule) ?? null,
        ...Object.fromEntries(OUTCOMES.map(outcome => [outcome, own.filter(firing => firing.outcome === outcome).length])),
        total: own.length,
        previous: firings.filter(firing => firing.rule === rule && within(firing.at, previous)).length,
        last: iso(Math.max(...own.map(firing => firing.at))),
    };
}

function within(moment, window) {
    return moment >= window.since && moment < window.until;
}
