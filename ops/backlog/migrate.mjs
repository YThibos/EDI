#!/usr/bin/env node
import { readFileSync } from 'node:fs';
import { dirname, join, posix } from 'node:path';
import { fileURLToPath } from 'node:url';

import {
    APPROVED, CLOUD, GitHub, HELD, LABEL_DESCRIPTIONS, MACHINE, Refusal, SECURITY, SESSION_MARKER, command, labelsOf, refusalsReported,
} from './backlog.mjs';

const USAGE = 'Usage: migrate.mjs [--dry-run] [--from REF] [--approve "THE CAPTAIN\'S WORD"] [--plan FILE] [--repo OWNER/NAME]';
const HERE = dirname(fileURLToPath(import.meta.url));
const ITEMS = 'docs/backlog';
const MIGRATED = /^(open|held|parked|needs-captain-machine)\b/;
const CLOSING = { delivered: 'completed', dropped: 'not_planned', folded: 'not_planned' };
const DROPPED_ROWS = ['Key', 'Wave and order', 'Blocked by', 'Status', 'Issue', 'Cloud session', 'Must land before'];
const WAVES = ['wave-1', 'wave-2', 'wave-3', 'wave-4', 'wave-5', 'wave-6'];
const KEY = /EDI-\d+/g;

function migrate(options) {
    const run = { dryRun: options.dryRun, changes: 0 };
    const github = GitHub.ofTheCheckout(options.repo);
    const source = new Source(options.from, github.repository);
    const plan = JSON.parse(readFileSync(options.plan, 'utf8'));
    const items = source.items();
    const migrated = plannedItemsOf(items, plan);
    settleTheLabels(run, github);
    const issues = new Issues(github);
    const milestones = settledMilestones(run, github, plan);
    const marker = settledMarker(run, github, issues, plan);
    migrated.forEach(item => settleTheIssue(run, github, issues, item, { source, plan, milestones, items, approval: options.approve }));
    migrated.forEach(item => settleTheParent(run, github, issues, item, plan, items));
    migrated.forEach(item => settleTheBlockers(run, github, issues, item, blockersOf(item, items, plan, marker)));
    settleTheMarkersBlockers(run, github, issues, marker, plan, items);
    items.filter(item => !MIGRATED.test(item.status)).forEach(item => settleTheDrift(run, github, issues, item, items));
    removeTheWaveLabels(run, github);
    console.log(run.changes === 0 ? 'Nothing to change.' : `${run.changes} ${run.dryRun ? 'changes would be made' : 'changes were made'}.`);
}

function optionsFrom(args) {
    const options = { dryRun: false, from: 'origin/main', plan: join(HERE, 'migration-plan.json') };
    for (let at = 0; at < args.length; at++) {
        if (args[at] === '--dry-run') {
            options.dryRun = true;
        } else if (['--from', '--approve', '--plan', '--repo'].includes(args[at]) && args[at + 1] !== undefined) {
            options[args[at].slice(2)] = args[++at];
        } else {
            throw new Refusal(USAGE);
        }
    }
    return options;
}

function act(run, action, deed) {
    run.changes++;
    console.log(run.dryRun ? `would ${action}` : `${action[0].toUpperCase()}${action.slice(1)}`);
    return run.dryRun ? null : deed();
}

class Source {

    constructor(ref, repository) {
        this.root = command('git', ['rev-parse', '--show-toplevel']).trim();
        this.ref = ref;
        this.web = `https://github.com/${repository}`;
        const files = command('git', ['-C', this.root, 'ls-tree', '--name-only', `${ref}:${ITEMS}`]).split('\n').filter(name => /^EDI-\d+\.md$/.test(name));
        if (files.length === 0) {
            throw new Refusal(`${ref} holds no item files under ${ITEMS}; pass --from with a commit that still has them.`);
        }
        this.files = files;
        this.commit = this.lastChangeOfTheItems(ref);
    }

    lastChangeOfTheItems(ref) {
        const changed = command('git', ['-C', this.root, 'log', '-1', '--format=%H', ref, '--', ITEMS]).trim();
        const same = command('git', ['-C', this.root, 'diff', '--name-only', changed, ref, '--', ITEMS]).trim() === '';
        return same ? changed : command('git', ['-C', this.root, 'rev-parse', ref]).trim();
    }

    items() {
        return this.files.map(name => itemOf(name.slice(0, -3), this.read(`${ITEMS}/${name}`)));
    }

    read(path) {
        return command('git', ['-C', this.root, 'show', `${this.ref}:${path}`]);
    }

    permalink(path) {
        return `${this.web}/blob/${this.commit}/${path}`;
    }
}

function itemOf(key, text) {
    const heading = new RegExp(`^# ${key} (.+)$`, 'm').exec(text);
    if (!heading) {
        throw new Refusal(`${key}.md does not open with "# ${key} <title>".`);
    }
    const lines = text.split('\n');
    const tableStart = lines.findIndex(line => line === '| Field | Value |');
    let tableEnd = tableStart + 2;
    while (tableEnd < lines.length && lines[tableEnd].startsWith('|')) {
        tableEnd++;
    }
    const rows = Object.fromEntries(lines.slice(tableStart + 2, tableEnd).map(row => {
        const cells = row.split(' | ');
        return [cells[0].slice(2).trim(), cells.slice(1).join(' | ').replace(/ \|$/, '').trim()];
    }));
    return {
        key,
        title: heading[1].trim(),
        rows,
        status: rows.Status ?? '',
        issue: Number(/#(\d+)/.exec(rows.Issue ?? '')?.[1]) || null,
        blockers: keysIn(rows['Blocked by']),
        before: keysIn(rows['Must land before']),
        spec: lines.slice(tableEnd).join('\n').trim(),
    };
}

function keysIn(text) {
    return [...new Set((text ?? '').match(KEY) ?? [])];
}

function plannedItemsOf(items, plan) {
    const migrated = items.filter(item => MIGRATED.test(item.status));
    const unplanned = migrated.filter(item => !plan.items[item.key]).map(item => item.key);
    if (unplanned.length > 0) {
        throw new Refusal(`The plan names no milestone for ${unplanned.join(', ')}; add each to the plan's items, with "milestone": null where none fits.`);
    }
    const unissued = migrated.filter(item => !item.issue).map(item => item.key);
    if (unissued.length > 0) {
        throw new Refusal(`${unissued.join(', ')} names no issue in its file; open it with the edi-backlog-item skill first.`);
    }
    const settled = Object.keys(plan.items).filter(key => !migrated.some(item => item.key === key));
    settled.forEach(key => console.log(`${key} is in the plan but is no longer open, held or parked; it is settled as drift instead.`));
    return migrated.sort((one, other) => one.issue - other.issue);
}

class Issues {

    constructor(github) {
        this.listed = github.all('issues?state=all').filter(issue => !issue.pull_request);
    }

    numbered(number) {
        return this.listed.find(issue => issue.number === number) ?? null;
    }

    titled(title) {
        return this.listed.find(issue => issue.title === title) ?? null;
    }

    add(issue) {
        this.listed.push(issue);
    }
}

function settleTheLabels(run, github) {
    const existing = github.all('labels');
    const named = name => existing.find(label => label.name === name);
    if (named('parked') && !named(HELD)) {
        act(run, `rename the label parked to ${HELD}, so its issues keep it`, () => github.patch('labels/parked', { new_name: HELD, description: LABEL_DESCRIPTIONS[HELD] }));
        existing.push({ name: HELD, description: LABEL_DESCRIPTIONS[HELD] });
    }
    for (const [name, description] of Object.entries(LABEL_DESCRIPTIONS)) {
        const label = named(name);
        if (!label) {
            act(run, `create the label ${name}: ${description}`, () => github.post('labels', { name, description }));
        } else if (label.description !== description) {
            act(run, `describe the label ${name}: ${description}`, () => github.patch(`labels/${encodeURIComponent(name)}`, { description }));
        }
    }
}

function settledMilestones(run, github, plan) {
    const existing = github.all('milestones?state=all');
    return Object.fromEntries(plan.milestones.map(planned => {
        const found = existing.find(milestone => milestone.title === planned.title);
        const fields = { title: planned.title, description: planned.description, ...(planned.due ? { due_on: `${planned.due}T12:00:00Z` } : {}) };
        if (!found) {
            const created = act(run, `create the milestone "${planned.title}"${planned.due ? `, due ${planned.due}` : ', with no due date'}`, () => github.post('milestones', fields));
            return [planned.title, created ?? { number: `<the new milestone "${planned.title}">`, title: planned.title }];
        }
        if (found.description !== planned.description || (found.due_on ?? '').slice(0, 10) !== (planned.due ?? '')) {
            act(run, `update the milestone "${planned.title}"${planned.due ? `, due ${planned.due}` : ''}`, () => github.patch(`milestones/${found.number}`, fields));
        }
        return [planned.title, found];
    }));
}

function settledMarker(run, github, issues, plan) {
    const found = issues.titled(plan.marker.title);
    if (found) {
        return found;
    }
    const created = act(run, `open the marker issue "${plan.marker.title}" labelled ${HELD}, ${MACHINE}:\n${indented(plan.marker.body)}`,
        () => github.post('issues', { title: plan.marker.title, body: plan.marker.body, labels: [HELD, MACHINE] }));
    if (created) {
        issues.add(created);
    }
    return created ?? { number: null, id: null, title: plan.marker.title };
}

function settleTheIssue(run, github, issues, item, context) {
    const issue = issues.numbered(item.issue) ?? missing(item);
    const title = `${item.key} ${item.title}`;
    const body = bodyOf(item, context);
    const milestone = context.plan.items[item.key].milestone ? context.milestones[context.plan.items[item.key].milestone] : null;
    const fields = {};
    if (issue.title !== title) {
        fields.title = title;
    }
    if ((issue.body ?? '').trim() !== body.trim()) {
        fields.body = body;
    }
    if ((issue.milestone?.number ?? null) !== (milestone?.number ?? null)) {
        fields.milestone = milestone?.number ?? null;
    }
    if (Object.keys(fields).length > 0) {
        act(run, `update #${issue.number} ${item.key}: ${describedFields(fields, milestone)}${fields.body ? `\n${indented(body)}` : ''}`,
            () => github.patch(`issues/${issue.number}`, fields));
    }
    settleTheIssuesLabels(run, github, issue, item, context);
}

function missing(item) {
    throw new Refusal(`${item.key} names issue #${item.issue}, which does not exist.`);
}

function describedFields(fields, milestone) {
    return [
        ...(fields.title ? [`title "${fields.title}"`] : []),
        ...('milestone' in fields ? [milestone ? `milestone "${milestone.title}"` : 'no milestone'] : []),
        ...(fields.body ? ['body'] : []),
    ].join(', ');
}

function bodyOf(item, context) {
    const kept = Object.entries(item.rows).filter(([name]) => !DROPPED_ROWS.includes(name));
    const header = ['| Field | Value |', '|---|---|', ...kept.map(([name, value]) => `| ${name} | ${linked(value, context)} |`)].join('\n');
    const spec = withoutTheDelivery(linked(item.spec, context));
    const moved = `The spec moved here from [${ITEMS}/${item.key}.md](${context.source.permalink(`${ITEMS}/${item.key}.md`)}).`;
    return `${header}\n\n${spec}\n${reasonOf(item.status)}\n${moved}\n`;
}

function linked(markdown, context) {
    let fenced = false;
    return markdown.split('\n').map(line => {
        fenced = /^\s*(```|~~~)/.test(line) ? !fenced : fenced;
        return fenced ? line : line.replace(/\]\(([^)\s]+)\)/g, (_, target) => `](${targetOf(target, context)})`);
    }).join('\n');
}

function targetOf(target, { source, items }) {
    if (/^[a-z][a-z+.-]*:/i.test(target) || target.startsWith('#')) {
        return target;
    }
    const [path, anchor] = target.split(/(?=#)/);
    const item = /^(EDI-\d+)\.md$/.exec(path);
    if (item) {
        const issue = items.find(candidate => candidate.key === item[1])?.issue;
        return issue ? `${source.web}/issues/${issue}` : source.permalink(`${ITEMS}/${path}`);
    }
    if (path === 'README.md') {
        return `${source.web}/blob/main/docs/pickup.md`;
    }
    const resolved = posix.normalize(posix.join(ITEMS, path));
    if (resolved.startsWith(`${ITEMS}/research/`)) {
        return `${source.web}/blob/main/docs/research/${resolved.slice(`${ITEMS}/research/`.length)}${anchor ?? ''}`;
    }
    if (resolved.startsWith(`${ITEMS}/`)) {
        return `${source.permalink(resolved)}${anchor ?? ''}`;
    }
    return `${source.web}/blob/main/${resolved}${anchor ?? ''}`;
}

function withoutTheDelivery(spec) {
    return spec.replace(/^## Delivery\n[\s\S]*?(?=^## |(?![\s\S]))/m, '').trim();
}

function reasonOf(status) {
    const reason = /^(held|parked|needs-captain-machine): *(.*)$/.exec(status);
    if (!reason) {
        return '';
    }
    const heading = reason[1] === 'needs-captain-machine' ? "Needs the captain's machine" : 'Held';
    return `\n## ${heading}\n\n${reason[2].charAt(0).toUpperCase()}${reason[2].slice(1)}\n`;
}

function indented(text) {
    return text.trimEnd().split('\n').map(line => `    ${line}`).join('\n');
}

function settleTheIssuesLabels(run, github, issue, item, context) {
    const current = labelsOf(issue).map(label => (label === 'parked' ? HELD : label));
    const wanted = labelsWantedFor(item, current, context);
    const added = wanted.filter(label => !current.includes(label));
    const removed = [CLOUD, MACHINE, HELD].filter(label => current.includes(label) && !wanted.includes(label));
    if (added.length > 0) {
        act(run, `label #${issue.number} ${item.key} ${added.join(', ')}${added.includes(APPROVED) ? `, on ${context.approval}` : ''}`,
            () => github.post(`issues/${issue.number}/labels`, { labels: added }));
    }
    for (const label of removed) {
        act(run, `remove ${label} from #${issue.number} ${item.key}`, () => github.delete(`issues/${issue.number}/labels/${encodeURIComponent(label)}`));
    }
}

function labelsWantedFor(item, current, { plan, approval }) {
    const machine = item.status.startsWith('needs-captain-machine') || current.includes(MACHINE);
    const held = /^(held|parked)\b/.test(item.status);
    const security = Boolean(item.rows.Severity) || plan.security.includes(item.key) || current.includes(SECURITY);
    return [
        machine ? MACHINE : CLOUD,
        ...(held ? [HELD] : []),
        ...(security ? [SECURITY] : []),
        ...(approval || current.includes(APPROVED) ? [APPROVED] : []),
    ];
}

function settleTheParent(run, github, issues, item, plan, items) {
    const parentKey = plan.items[item.key].parent;
    if (!parentKey) {
        return;
    }
    const parent = items.find(candidate => candidate.key === parentKey);
    if (!parent?.issue) {
        throw new Refusal(`The plan makes ${item.key} a sub-issue of ${parentKey}, which names no issue.`);
    }
    const linked = github.all(`issues/${parent.issue}/sub_issues`).some(sub => sub.number === item.issue);
    if (!linked) {
        act(run, `make #${item.issue} ${item.key} a sub-issue of #${parent.issue} ${parentKey}`,
            () => github.post(`issues/${parent.issue}/sub_issues`, { sub_issue_id: issues.numbered(item.issue).id }));
    }
}

function blockersOf(item, items, plan, marker) {
    const blockers = item.blockers.map(key => ({ key, issue: items.find(candidate => candidate.key === key)?.issue ?? null }));
    const before = items.filter(other => other.before.includes(item.key)).map(other => ({ key: other.key, issue: other.issue }));
    const marked = plan.marker.blocks.includes(item.key) ? [{ key: plan.marker.title, issue: marker.number, id: marker.id }] : [];
    return [...blockers, ...before.filter(other => !item.blockers.includes(other.key)), ...marked];
}

function settleTheBlockers(run, github, issues, item, blockers) {
    const linked = github.all(`issues/${item.issue}/dependencies/blocked_by`).map(blocker => blocker.number);
    for (const blocker of blockers) {
        if (blocker.issue === null && blocker.id === undefined) {
            console.log(`#${item.issue} ${item.key} keeps ${blocker.key} as text: ${blocker.key} has no issue, since it was delivered before the issues existed.`);
        } else if (!linked.includes(blocker.issue)) {
            act(run, `mark #${item.issue} ${item.key} blocked by ${blocker.issue ? `#${blocker.issue} ` : ''}${blocker.key}`,
                () => github.post(`issues/${item.issue}/dependencies/blocked_by`, { issue_id: blocker.id ?? issues.numbered(blocker.issue).id }));
        }
    }
}

function settleTheMarkersBlockers(run, github, issues, marker, plan, items) {
    const blockers = plan.marker.blockedBy.map(key => items.find(item => item.key === key));
    const linked = marker.number === null ? [] : github.all(`issues/${marker.number}/dependencies/blocked_by`).map(blocker => blocker.number);
    for (const blocker of blockers.filter(candidate => !linked.includes(candidate.issue))) {
        act(run, `mark the marker issue "${plan.marker.title}" blocked by #${blocker.issue} ${blocker.key}`,
            () => github.post(`issues/${marker.number}/dependencies/blocked_by`, { issue_id: issues.numbered(blocker.issue).id }));
    }
}

function settleTheDrift(run, github, issues, item, items) {
    const issue = item.issue ? issues.numbered(item.issue) : null;
    const settled = /^(delivered|dropped|folded)\b/.exec(item.status)?.[1];
    if (!issue || issue.state !== 'open' || !settled) {
        return;
    }
    const carrier = settled === 'folded' ? items.find(other => other.key === (item.status.match(KEY) ?? [])[0]) : null;
    if (carrier) {
        const note = `${SESSION_MARKER} ${item.key} was folded into ${carrier.key} (#${carrier.issue}), which carries its criteria, so this issue closes as not planned.`;
        act(run, `comment on #${issue.number} ${item.key}: ${note}`, () => github.post(`issues/${issue.number}/comments`, { body: note }));
    }
    act(run, `close #${issue.number} ${item.key} as ${CLOSING[settled].replace('_', ' ')}, since its file says ${settled}`,
        () => github.patch(`issues/${issue.number}`, { state: 'closed', state_reason: CLOSING[settled] }));
}

function removeTheWaveLabels(run, github) {
    const existing = github.all('labels').map(label => label.name);
    for (const wave of WAVES.filter(name => existing.includes(name))) {
        act(run, `remove the label ${wave} from the repository`, () => github.delete(`labels/${wave}`));
    }
}

refusalsReported(() => migrate(optionsFrom(process.argv.slice(2))));
