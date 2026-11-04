#!/usr/bin/env node
import { readFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { join } from 'node:path';

import {
    APPROVED, CLOUD, GitHub, HELD, LABEL_DESCRIPTIONS, MACHINE, Refusal, command, keyOf, labelsOf, missingSectionsOf, plainTitleOf, refusalsReported,
    repositoryOfTheRemote,
} from '../../../ops/backlog/backlog.mjs';

const USAGE = 'Usage: backlog-item.mjs [--dry-run] DRAFT...';
const PROJECT = 'EDI';
const DRAFT_FIELDS = ['task', 'title', 'kind', 'branch', 'source', 'size', 'milestone', 'parent', 'blocked-by', 'status', 'labels', 'captain-approved'];
const SIZES = ['XS', 'S', 'M', 'L'];
const STATUSES = /^(open|(held|needs-captain-machine): \S.*)$/;
const REFERENCE = /^(EDI-\d+|#\d+)$/;

function backlog(run) {
    const drafts = run.draftPaths.map(draftAt);
    const github = upstreamOf(ediClone());
    const known = new KnownIssues(github);
    const items = drafts.map(draft => specifiedItem(draft, known));
    for (const item of items) {
        const issue = issueOf(run, github, known, item);
        linkTheParent(run, github, issue, item);
        linkTheBlockers(run, github, issue, item);
        approve(run, github, issue, item);
        printSummary(item, issue, holdLocally(run, item, issue));
    }
}

function runFrom(args) {
    const dryRun = args.includes('--dry-run');
    const draftPaths = args.filter(arg => arg !== '--dry-run');
    if (draftPaths.length === 0 || draftPaths.some(path => path.startsWith('-'))) {
        throw new Refusal(USAGE);
    }
    return { dryRun, draftPaths };
}

function draftAt(path) {
    const text = draftTextAt(path);
    const fence = text.startsWith('---\n') ? text.indexOf('\n---\n', 3) : -1;
    if (fence < 0) {
        throw new Refusal(`${path} starts with its fields between two --- lines, then the spec.`);
    }
    return { path, fields: fieldsOf(path, text.slice(4, fence)), spec: text.slice(fence + 5).trim() };
}

function draftTextAt(path) {
    try {
        return readFileSync(path, 'utf8');
    } catch (unreadable) {
        throw new Refusal(`The draft ${path} cannot be read: ${unreadable.message}`);
    }
}

function fieldsOf(path, block) {
    const fields = {};
    for (const line of block.split('\n').filter(line => line.trim() !== '')) {
        const [name, ...value] = line.split(':');
        if (value.length === 0 || !DRAFT_FIELDS.includes(name.trim())) {
            throw new Refusal(`${path}: "${line}" is not one of the fields ${DRAFT_FIELDS.join(', ')}, followed by ": " and its value.`);
        }
        fields[name.trim()] = value.join(':').trim();
    }
    return fields;
}

function ediClone() {
    return join(process.env.EDI_HOME ?? join(homedir(), '.edi'), 'projects', PROJECT);
}

function upstreamOf(clone) {
    const repository = repositoryOfTheRemote(clone);
    const host = /^(?:https?:\/\/|git@)([^/:]+)/.exec(command('git', ['-C', clone, 'remote', 'get-url', 'origin']).trim())?.[1] ?? 'github.com';
    return new GitHub(repository, ghEnvironment(clone, host));
}

function ghEnvironment(clone, host) {
    const account = pinnedAccount(clone, host);
    const prompts = { GH_PROMPT_DISABLED: '1' };
    return account ? { ...prompts, GH_TOKEN: command('gh', ['auth', 'token', '--hostname', host, '--user', account]).trim() } : prompts;
}

function pinnedAccount(clone, host) {
    try {
        return command('git', ['-C', clone, 'config', '--local', '--get', `credential.https://${host}.ghPinUser`]).trim();
    } catch {
        return null;
    }
}

function specifiedItem(draft, known) {
    const { fields, path } = draft;
    const task = fields.task ? taskNamed(fields.task) : taskTitled(fields.title);
    const kind = fields.kind ?? task?.intake.kind ?? 'ship';
    requireOneOf(path, 'kind', kind, ['ship', 'scout']);
    requireOneOf(path, 'branch', fields.branch ?? branchKindOf(kind), ['feature', 'chore', 'research']);
    requireOneOf(path, 'size', fields.size, SIZES);
    requireStatus(path, fields.status);
    return {
        path,
        task,
        title: requiredField(path, 'title', fields.title ?? task?.intake.title),
        kind,
        branch: fields.branch ?? branchKindOf(kind),
        source: requiredField(path, 'source', fields.source),
        size: fields.size,
        milestone: milestoneOf(path, known, fields.milestone),
        parent: fields.parent ? referencedIssue(path, known, fields.parent) : null,
        blockers: listOf(fields['blocked-by']).filter(entry => entry !== '-').map(entry => referencedIssue(path, known, entry)),
        status: fields.status,
        labels: listOf(fields.labels),
        approval: fields['captain-approved'] ?? null,
        spec: specOf(path, draft.spec || task?.notes || ''),
    };
}

function taskNamed(id) {
    const task = JSON.parse(command('edi', ['task', 'show', id, '--json'])).task;
    if (!task.intake.projects.includes(PROJECT)) {
        throw new Refusal(`${id} works on ${task.intake.projects.join(', ')}, not on ${PROJECT}.`);
    }
    return task;
}

function taskTitled(title) {
    const listed = JSON.parse(command('edi', ['task', 'list', '--json'])).tasks;
    const found = listed.find(task => task.intake.title === title && task.intake.projects.includes(PROJECT));
    return found ? taskNamed(found.id) : null;
}

function requireOneOf(path, name, value, allowed) {
    if (!allowed.includes(value)) {
        throw new Refusal(`${path}: ${name} is one of ${allowed.join(', ')}, not "${value ?? ''}".`);
    }
}

function branchKindOf(kind) {
    return kind === 'scout' ? 'research' : 'feature';
}

function requireStatus(path, status) {
    if (!STATUSES.test(status ?? '')) {
        throw new Refusal(`${path}: the status is open, or held or needs-captain-machine followed by ": " and the reason.`);
    }
}

function requiredField(path, name, value) {
    if (!value) {
        throw new Refusal(`${path}: the field "${name}" is missing.`);
    }
    return value;
}

function milestoneOf(path, known, title) {
    if (!title || title === '-') {
        return null;
    }
    const milestone = known.milestones().find(candidate => candidate.title === title);
    if (!milestone) {
        throw new Refusal(`${path}: no open milestone is titled "${title}"; the open ones are ${known.milestones().map(candidate => `"${candidate.title}"`).join(', ')}.`);
    }
    return milestone;
}

function referencedIssue(path, known, reference) {
    if (!REFERENCE.test(reference)) {
        throw new Refusal(`${path}: "${reference}" is neither a work key such as EDI-85 nor an issue such as #85.`);
    }
    const issue = reference.startsWith('#') ? known.numbered(Number(reference.slice(1))) : known.keyed(reference);
    if (!issue) {
        throw new Refusal(`${path}: ${reference} names no issue upstream.`);
    }
    return issue;
}

function listOf(field) {
    return (field ?? '').split(',').map(entry => entry.trim()).filter(entry => entry !== '');
}

function specOf(path, text) {
    const spec = withSecondLevelHeadings(text.trim());
    const missing = missingSectionsOf(spec);
    if (missing.length > 0) {
        throw new Refusal(`${path}: the spec lacks the sections ${missing.map(section => `"## ${section}"`).join(', ')}.`);
    }
    if (/^(- )?TODO:/m.test(spec)) {
        throw new Refusal(`${path}: the spec still holds a TODO line; replace each one before the item is backlogged.`);
    }
    return spec;
}

function withSecondLevelHeadings(markdown) {
    let fenced = false;
    return markdown.split('\n').map(line => {
        fenced = /^\s*(```|~~~)/.test(line) ? !fenced : fenced;
        return !fenced && line.startsWith('# ') ? `#${line}` : line;
    }).join('\n');
}

function act(run, action, deed) {
    console.log(run.dryRun ? `would ${action}` : `${action[0].toUpperCase()}${action.slice(1)}`);
    return run.dryRun ? null : deed();
}

function issueOf(run, github, known, item) {
    const existing = existingIssueOf(known, item);
    if (existing) {
        return keyed(run, github, existing);
    }
    const labels = labelsFor(item);
    ensureLabels(run, github, known, labels);
    const taskKey = item.task?.intake.workKey;
    const title = taskKey ? `${taskKey} ${item.title}` : item.title;
    const opened = act(run, `open the issue "${title}" labelled ${labels.join(', ')}${item.milestone ? ` in the milestone "${item.milestone.title}"` : ''}:\n${indented(bodyOf(item))}`,
        () => github.post('issues', { title, body: bodyOf(item), labels, ...(item.milestone ? { milestone: item.milestone.number } : {}) }));
    if (!opened) {
        return { number: null, id: null, key: taskKey ?? `${PROJECT}-<the new issue's number>`, url: 'a new issue', labels };
    }
    known.add(opened);
    return keyed(run, github, opened);
}

function existingIssueOf(known, item) {
    const taskKey = item.task?.intake.workKey;
    const byKey = taskKey ? known.keyed(taskKey) : null;
    if (byKey && plainTitleOf(byKey.title) !== item.title) {
        throw new Refusal(`${taskKey} already names "${plainTitleOf(byKey.title)}" upstream, not "${item.title}"; ${item.task.id} needs a key upstream has not used.`);
    }
    return byKey ?? known.titled(item.title);
}

function keyed(run, github, opened) {
    const key = keyOf(opened.title) ?? `${PROJECT}-${opened.number}`;
    if (!keyOf(opened.title)) {
        act(run, `title issue #${opened.number} "${key} ${opened.title}"`, () => github.patch(`issues/${opened.number}`, { title: `${key} ${opened.title}` }));
    }
    return { ...opened, key, url: opened.html_url, labels: labelsOf(opened) };
}

function labelsFor(item) {
    const machine = item.status.startsWith('needs-captain-machine') ? MACHINE : CLOUD;
    const held = item.status.startsWith('held') ? [HELD] : [];
    return [...new Set([machine, ...held, ...item.labels])].filter(label => label !== APPROVED);
}

function ensureLabels(run, github, known, labels) {
    for (const label of labels.filter(name => !known.labels().includes(name))) {
        act(run, `create the label ${label}`, () => github.post('labels', { name: label, description: LABEL_DESCRIPTIONS[label] ?? 'A backlog label' }));
    }
}

function bodyOf(item) {
    const header = [
        '| Field | Value |',
        '|---|---|',
        `| Kind | ${item.kind === 'scout' ? 'scout, research that ends in a report' : 'ship, a change to the repository'}, on a \`${item.branch}/\` branch named by its key |`,
        `| Source | ${item.source} |`,
        `| Size | ${item.size} |`,
    ].join('\n');
    return `${header}\n\n${item.spec}\n${reasonOf(item.status)}`;
}

function reasonOf(status) {
    const reason = /^(held|needs-captain-machine): (.*)$/.exec(status);
    if (!reason) {
        return '';
    }
    return `\n## ${reason[1] === 'held' ? 'Held' : "Needs the captain's machine"}\n\n${reason[2]}\n`;
}

function indented(text) {
    return text.trimEnd().split('\n').map(line => `    ${line}`).join('\n');
}

function linkTheParent(run, github, issue, item) {
    if (!item.parent) {
        return;
    }
    const linked = issue.number !== null && github.all(`issues/${item.parent.number}/sub_issues`).some(sub => sub.number === issue.number);
    if (!linked) {
        act(run, `make ${issue.key} a sub-issue of #${item.parent.number}`, () => github.post(`issues/${item.parent.number}/sub_issues`, { sub_issue_id: issue.id }));
    }
}

function linkTheBlockers(run, github, issue, item) {
    const linked = issue.number === null ? [] : github.all(`issues/${issue.number}/dependencies/blocked_by`).map(blocker => blocker.number);
    for (const blocker of item.blockers.filter(candidate => !linked.includes(candidate.number))) {
        act(run, `mark ${issue.key} blocked by #${blocker.number}`, () => github.post(`issues/${issue.number}/dependencies/blocked_by`, { issue_id: blocker.id }));
    }
}

function approve(run, github, issue, item) {
    if (!item.approval || issue.labels.includes(APPROVED)) {
        return;
    }
    act(run, `add ${APPROVED} to ${issue.key}, on ${item.approval}`, () => github.post(`issues/${issue.number}/labels`, { labels: [APPROVED] }));
}

function holdLocally(run, item, issue) {
    const task = item.task ?? queuedTask(run, item, issue);
    const note = `backlogged upstream as ${issue.url}; it is taken from there, never from the local queue`;
    const name = task?.id ?? `the new task "${item.title}"`;
    if (!task?.hold) {
        const reason = `${note[0].toUpperCase()}${note.slice(1)}`;
        act(run, `hold ${name} for a deferral: ${reason}`, () => command('edi', ['hold', name, '--kind', 'deferral', '--reason', reason]));
        return reason;
    }
    if (task.hold.reason.includes(issue.url)) {
        return task.hold.reason;
    }
    const reason = `${task.hold.reason}; ${note}`;
    act(run, `release ${name} and hold it again with its reason updated: ${reason}`, () => {
        command('edi', ['release', name]);
        command('edi', ['hold', name, '--kind', task.hold.kind, '--reason', reason, ...untilOf(task.hold)]);
    });
    return reason;
}

function queuedTask(run, item, issue) {
    const added = act(run, `queue the ${item.kind} "${item.title}" on ${issue.key} with edi task add`, () =>
        queuedTaskIn(command('edi', ['task', 'add', '--kind', item.kind, '--project', PROJECT, '--title', item.title, '--key', issue.key, '--body', '-'], item.spec)));
    return added ? taskNamed(added) : null;
}

function queuedTaskIn(answer) {
    const queued = new RegExp(`^Queued (\\S+) on ${PROJECT}-\\d+(: .*)?\\.$`).exec(answer.trim());
    if (!queued) {
        throw new Refusal(`edi task add answered "${answer.trim()}", which names no task queued on an ${PROJECT}-<n> key.`);
    }
    return queued[1];
}

function untilOf(hold) {
    return /^\d{4}-\d{2}-\d{2}$/.test(hold.until ?? '') ? ['--until', hold.until] : [];
}

function printSummary(item, issue, held) {
    console.log([
        '',
        `${issue.key} ${item.title}`,
        `  issue: ${issue.url}`,
        `  hold:  ${item.task?.id ?? 'the new task'}: ${held}`,
    ].join('\n'));
}

class KnownIssues {

    constructor(github) {
        this.github = github;
        this.issues = github.all('issues?state=all').filter(issue => !issue.pull_request);
    }

    keyed(key) {
        return this.issues.find(issue => keyOf(issue.title) === key) ?? null;
    }

    numbered(number) {
        return this.issues.find(issue => issue.number === number) ?? null;
    }

    titled(title) {
        return this.issues.find(issue => issue.state === 'open' && plainTitleOf(issue.title) === title) ?? null;
    }

    add(issue) {
        this.issues.unshift(issue);
    }

    milestones() {
        this.listedMilestones ??= this.github.all('milestones?state=open');
        return this.listedMilestones;
    }

    labels() {
        this.listedLabels ??= this.github.all('labels').map(label => label.name);
        return this.listedLabels;
    }
}

refusalsReported(() => backlog(runFrom(process.argv.slice(2))));
