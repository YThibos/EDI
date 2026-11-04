#!/usr/bin/env node
import { execFileSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { homedir, tmpdir } from 'node:os';
import { dirname, join } from 'node:path';

const USAGE = 'Usage: backlog-item.mjs [--dry-run] DRAFT...';
const PROJECT = 'EDI';
const BASE = 'main';
const BACKLOG = 'docs/backlog';
const INDEX = `${BACKLOG}/README.md`;
const DRAFT_FIELDS = ['task', 'title', 'kind', 'branch', 'source', 'size', 'severity', 'section', 'blocked-by', 'status', 'labels'];
const SIZES = ['XS', 'S', 'M', 'L'];
const STATUSES = /^(open|(held|parked|needs-captain-machine): \S.*)$/;
const SPEC_SECTIONS = ['Goal', 'Context', 'Acceptance criteria', 'Requirements from decisions and answers', 'Read first', 'How to verify'];
const DELIVERY = `## Delivery

Deliver this item as the [pickup protocol](README.md#pickup-protocol) says: a draft pull request that names the item and closes its issue, local verification in the pull request body, a question with lettered options instead of a guess, and this file's status updated in the same pull request.
`;
const LABEL_DESCRIPTIONS = {
    'cloud-ok': 'A cloud session may take it',
    'needs-captain-machine': "Needs the captain's machine",
    parked: 'Held or parked until its reason clears',
    security: 'A security finding',
};

class Refusal extends Error {}

function main() {
    try {
        backlog(runFrom(process.argv.slice(2)));
    } catch (failure) {
        if (!(failure instanceof Refusal)) {
            throw failure;
        }
        console.error(failure.message);
        process.exitCode = 1;
    }
}

function backlog(run) {
    const drafts = run.draftPaths.map(draftAt);
    const upstream = upstreamOf(ediClone());
    fetchTheBase(upstream);
    const index = indexOn(upstream, `origin/${BASE}`);
    const specified = drafts.map(draft => specifiedItem(draft, index));
    const items = specified.map((item, position) => keyed(run, item, position));
    const branch = `chore/${items[0].key}`;
    const delivered = deliverInACopy(run, upstream, branch, items);
    const held = items.map(item => holdLocally(run, item, delivered));
    items.forEach((item, position) => printSummary(item, delivered, held[position]));
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
    const origin = git(clone, 'config', '--get', 'remote.origin.url').trim();
    const address = /^(?:https:\/\/|git@)([^/:]+)[/:]([^/]+\/[^/]+?)(?:\.git)?\/?$/.exec(origin);
    if (!address) {
        throw new Refusal(`${clone}'s origin, ${origin}, is not a GitHub repository address.`);
    }
    const [, host, path] = address;
    return { clone, repository: `${host}/${path}`, web: `https://${host}/${path}`, gh: ghEnvironment(clone, host) };
}

function ghEnvironment(clone, host) {
    const account = pinnedAccount(clone, host);
    const prompts = { GH_PROMPT_DISABLED: '1' };
    return account ? { ...prompts, GH_TOKEN: command('gh', ['auth', 'token', '--hostname', host, '--user', account]).trim() } : prompts;
}

function pinnedAccount(clone, host) {
    try {
        return git(clone, 'config', '--local', '--get', `credential.https://${host}.ghPinUser`).trim();
    } catch {
        return null;
    }
}

function fetchTheBase(upstream) {
    git(upstream.clone, 'fetch', '--quiet', '--prune', 'origin');
}

function indexOn(upstream, ref) {
    return new BacklogIndex(git(upstream.clone, 'show', `${ref}:${INDEX}`));
}

function specifiedItem(draft, index) {
    const { fields, path } = draft;
    const task = fields.task ? taskNamed(fields.task) : taskTitled(fields.title);
    const kind = fields.kind ?? task?.intake.kind ?? 'ship';
    requireOneOf(path, 'kind', kind, ['ship', 'scout']);
    requireOneOf(path, 'branch', fields.branch ?? branchKindOf(kind), ['feature', 'chore', 'research']);
    requireOneOf(path, 'size', fields.size, SIZES);
    requireStatus(path, fields.status);
    requireSeverityWhereRanked(path, fields, index.section(fields.section ?? ''));
    return {
        task,
        title: requiredField(path, 'title', fields.title ?? task?.intake.title),
        kind,
        branch: fields.branch ?? branchKindOf(kind),
        source: requiredField(path, 'source', fields.source),
        size: fields.size,
        severity: fields.severity,
        section: fields.section,
        blockers: blockersOf(path, fields['blocked-by']),
        status: fields.status,
        labels: listOf(fields.labels),
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
        throw new Refusal(`${path}: the status is open, or held, parked or needs-captain-machine followed by ": " and the reason.`);
    }
}

function requireSeverityWhereRanked(path, fields, section) {
    if (section.ranksBySeverity && !fields.severity) {
        throw new Refusal(`${path}: the section "${section.heading}" ranks its items by severity, so the draft names one.`);
    }
}

function requiredField(path, name, value) {
    if (!value) {
        throw new Refusal(`${path}: the field "${name}" is missing.`);
    }
    return value;
}

function blockersOf(path, field) {
    const blockers = listOf(field).filter(key => key !== '-');
    const unkeyed = blockers.find(key => !/^EDI-\d+$/.test(key));
    if (unkeyed) {
        throw new Refusal(`${path}: the blocker "${unkeyed}" is not a work key such as EDI-85.`);
    }
    return blockers;
}

function listOf(field) {
    return (field ?? '').split(',').map(entry => entry.trim()).filter(entry => entry !== '');
}

function specOf(path, text) {
    const spec = withSecondLevelHeadings(text.trim());
    const missing = SPEC_SECTIONS.filter(section => !new RegExp(`^## ${section}$`, 'm').test(spec));
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

function keyed(run, item, position) {
    if (item.task) {
        return { ...item, task: item.task.id, key: keyOf(item.task), hold: item.task.hold };
    }
    const added = act(run, `queue the ${item.kind} "${item.title}" with edi task add, which allocates its key`, () =>
        queuedTaskIn(command('edi', ['task', 'add', '--kind', item.kind, '--project', PROJECT, '--title', item.title, '--body', '-'], item.spec)));
    if (!added) {
        return { ...item, task: null, key: `${PROJECT}-<new ${position + 1}>`, hold: null };
    }
    const task = taskNamed(added);
    return { ...item, task: task.id, key: keyOf(task), hold: task.hold };
}

function queuedTaskIn(answer) {
    const queued = new RegExp(`^Queued (\\S+) on ${PROJECT}-\\d+(: .*)?\\.$`).exec(answer.trim());
    if (!queued) {
        throw new Refusal(`edi task add answered "${answer.trim()}", which names no task queued on an ${PROJECT}-<n> key.`);
    }
    return queued[1];
}

function keyOf(task) {
    if (!task.intake.workKey) {
        throw new Refusal(`${task.id} has no work key; a ship or scout on the ${PROJECT} project takes its key when edi task add queues it.`);
    }
    return task.intake.workKey;
}

function act(run, action, deed) {
    console.log(run.dryRun ? `would ${action}` : `${action[0].toUpperCase()}${action.slice(1)}`);
    return run.dryRun ? null : deed();
}

function deliverInACopy(run, upstream, branch, items) {
    const base = awaitsMerging(upstream, branch) ? `origin/${branch}` : `origin/${BASE}`;
    const known = issuesByKey(upstream);
    requireKeysOfTheirOwn(indexOn(upstream, base), known, items);
    const copy = new ThrowawayCopy(run, upstream, base);
    try {
        const index = addTheItems(copy, upstream, base, items);
        const issues = issuesOf(run, upstream, items, known);
        nameTheIssues(copy, items, issues);
        const unmerged = copy.commits > 0 || base !== `origin/${BASE}`;
        return { issues, unmerged, pull: unmerged ? pullRequestFor(run, upstream, copy, branch, items, issues) : null };
    } finally {
        copy.remove();
    }
}

function awaitsMerging(upstream, branch) {
    const pushed = git(upstream.clone, 'for-each-ref', '--format=%(refname)', `refs/remotes/origin/${branch}`).trim() !== '';
    return pushed && git(upstream.clone, 'rev-list', '--count', `origin/${BASE}..origin/${branch}`).trim() !== '0';
}

function requireKeysOfTheirOwn(index, issues, items) {
    for (const item of items) {
        const other = [index.titleOf(item.key), titleOfTheIssue(item.key, issues)].find(title => title !== undefined && title !== item.title);
        if (other !== undefined) {
            throw new Refusal(`${item.key} already names "${other}" upstream, not "${item.title}"; ${item.task} needs a key upstream has not used before it is backlogged.`);
        }
    }
}

function titleOfTheIssue(key, issues) {
    return issues[key]?.title.slice(key.length + 1);
}

function addTheItems(copy, upstream, base, items) {
    const takenElsewhere = ordersAwaitingMerge(upstream);
    let index = indexOn(upstream, base);
    for (const item of items) {
        const withRow = index.names(item.key) ? index : index.withRow(item, takenElsewhere);
        const writesTheFile = !existsOn(upstream, base, itemPath(item.key));
        if (withRow !== index) {
            copy.write(INDEX, withRow.text, `add ${item.key}'s row to ${INDEX}: ${withRow.rowOf(item.key)}`);
        }
        if (writesTheFile) {
            const file = itemFile(item, withRow);
            copy.write(itemPath(item.key), file, `write ${itemPath(item.key)}:\n${indented(file)}`);
        }
        if (writesTheFile || withRow !== index) {
            copy.commit(`${item.key} Add the backlog item: ${item.title}`);
        }
        index = withRow;
    }
    return index;
}

function ordersAwaitingMerge(upstream) {
    const branches = git(upstream.clone, 'for-each-ref', '--format=%(refname)', '--no-merged', `origin/${BASE}`, 'refs/remotes/origin').split('\n');
    return branches.filter(ref => ref !== '' && existsOn(upstream, ref, INDEX)).flatMap(ref => indexOn(upstream, ref).orders());
}

function existsOn(upstream, ref, path) {
    try {
        git(upstream.clone, 'cat-file', '-e', `${ref}:${path}`);
        return true;
    } catch {
        return false;
    }
}

function itemPath(key) {
    return `${BACKLOG}/${key}.md`;
}

function itemFile(item, index) {
    const fields = [
        ['Key', item.key],
        ['Kind', `${kindSentence(item.kind)}, on branch \`${item.branch}/${item.key}\``],
        ['Wave and order', index.placeOf(item.key)],
        ['Source', item.source],
        ['Size', item.size],
        ...(item.severity ? [['Severity', item.severity]] : []),
        ['Blocked by', item.blockers.length === 0 ? 'nothing' : item.blockers.map(key => `[${key}](${key}.md)`).join(', ')],
        ['Status', item.status],
        ['Issue', '-'],
        ['Cloud session', cloudSessionOf(item.status)],
    ];
    const header = ['| Field | Value |', '|---|---|', ...fields.map(([name, value]) => `| ${name} | ${value} |`)].join('\n');
    const delivery = /^## Delivery$/m.test(item.spec) ? '' : `\n${DELIVERY}`;
    return `# ${item.key} ${item.title}\n\n${header}\n\n${item.spec}\n${delivery}`;
}

function kindSentence(kind) {
    return kind === 'scout' ? 'scout, research that ends in a report' : 'ship, a change to the repository';
}

function cloudSessionOf(status) {
    if (status === 'open') {
        return 'ok';
    }
    return status.startsWith('needs-captain-machine') ? 'not yet: see the status' : 'ok, once open';
}

function indented(text) {
    return text.trimEnd().split('\n').map(line => `    ${line}`).join('\n');
}

function issuesOf(run, upstream, items, issues) {
    for (const item of items) {
        issues[item.key] = issues[item.key] || openedIssue(run, upstream, item, issues);
    }
    return issues;
}

function issuesByKey(upstream) {
    const listed = JSON.parse(gh(upstream, ['issue', 'list', '--state', 'all', '--limit', '1000', '--json', 'number,title,url']));
    const entries = listed.map(issue => [workKeyOf(issue.title), { number: issue.number, title: issue.title, url: issue.url }]).filter(([key]) => key);
    return Object.fromEntries(entries.reverse());
}

function workKeyOf(title) {
    return new RegExp(`^(${PROJECT}-\\d+) `).exec(title)?.[1];
}

function openedIssue(run, upstream, item, known) {
    const labels = labelsOf(item);
    ensureLabels(run, upstream, labels);
    const title = `${item.key} ${item.title}`;
    const url = act(run, `open the issue "${title}" labelled ${labels.join(', ')}`, () =>
        lastLineOf(gh(upstream, ['issue', 'create', '--title', title, '--label', labels.join(','), '--body-file', '-'], issueBody(upstream, item, known))));
    return url ? { number: Number(url.split('/').at(-1)), url } : null;
}

function labelsOf(item) {
    const machine = item.status.startsWith('needs-captain-machine') ? 'needs-captain-machine' : 'cloud-ok';
    const parked = /^(held|parked):/.test(item.status) ? ['parked'] : [];
    return [...new Set([...sectionLabelOf(item.section), machine, ...parked, ...item.labels])];
}

function sectionLabelOf(section) {
    const wave = /^wave\s+(\d+)/i.exec(section);
    if (wave) {
        return [`wave-${wave[1]}`];
    }
    return /^security/i.test(section) ? ['security'] : [];
}

function ensureLabels(run, upstream, labels) {
    const existing = JSON.parse(gh(upstream, ['label', 'list', '--limit', '500', '--json', 'name'])).map(label => label.name);
    for (const label of labels.filter(label => !existing.includes(label))) {
        act(run, `create the label ${label}`, () => gh(upstream, ['label', 'create', label, '--description', labelDescriptionOf(label)]));
    }
}

function labelDescriptionOf(label) {
    const wave = /^wave-(\d+)$/.exec(label);
    return wave ? `Backlog wave ${wave[1]}` : LABEL_DESCRIPTIONS[label] ?? 'A backlog label';
}

function lastLineOf(output) {
    return output.trim().split('\n').at(-1);
}

function issueBody(upstream, item, known) {
    const spec = `The spec is [${itemPath(item.key)}](${upstream.web}/blob/${BASE}/${itemPath(item.key)}).`;
    if (item.blockers.length === 0) {
        return `${spec}\n`;
    }
    const blockers = item.blockers.map(key => known[key] ? `#${known[key].number}` : key);
    return `${spec}\nBlocked by ${blockers.join(', ')}.\n`;
}

function nameTheIssues(copy, items, issues) {
    for (const item of items) {
        const issue = issues[item.key];
        const file = copy.read(itemPath(item.key));
        const named = issue ? withIssue(file, issue) : file;
        if (named !== file) {
            copy.write(itemPath(item.key), named, `name ${item.key}'s issue in ${itemPath(item.key)}: [#${issue.number}](${issue.url})`);
            copy.commit(`${item.key} Name the issue of the backlog item`);
        }
    }
}

function withIssue(file, issue) {
    const field = `| Issue | [#${issue.number}](${issue.url}) |`;
    if (/^\| Issue \| -? *\|$/m.test(file)) {
        return file.replace(/^\| Issue \| -? *\|$/m, field);
    }
    return /^\| Issue \|/m.test(file) ? file : file.replace(/^(\| Status \| .* \|)$/m, `$1\n${field}`);
}

function pullRequestFor(run, upstream, copy, branch, items, issues) {
    if (copy.commits > 0) {
        copy.push(branch);
    }
    const openPull = openPullFrom(upstream, branch);
    if (openPull) {
        return openPull;
    }
    const title = items.length === 1 ? `${items[0].key} Backlog: ${items[0].title}` : `${items[0].key} Backlog: ${keysOf(items)}`;
    return act(run, `open the pull request "${title}" from ${branch} into ${BASE}, ready for review`, () =>
        lastLineOf(gh(upstream, ['pr', 'create', '--base', BASE, '--head', branch, '--title', title, '--body-file', '-'], pullBody(upstream, branch, items, issues))));
}

function openPullFrom(upstream, branch) {
    const pulls = JSON.parse(gh(upstream, ['pr', 'list', '--head', branch, '--state', 'open', '--json', 'url']));
    return pulls[0]?.url ?? null;
}

function keysOf(items) {
    const keys = items.map(item => item.key);
    return `${keys.slice(0, -1).join(', ')} and ${keys.at(-1)}`;
}

function pullBody(upstream, branch, items, issues) {
    const files = items.map(item => `- [${itemPath(item.key)}](${upstream.web}/blob/${branch}/${itemPath(item.key)}): ${item.title}, tracked by ${issues[item.key].url}`);
    return [
        `Adds to the [backlog](${upstream.web}/blob/${branch}/${INDEX}) the items below, each with its row in the index and its issue.`,
        '',
        ...files,
        '',
        'The change is Markdown under `docs/backlog` only, so no build ran.',
        '',
    ].join('\n');
}

function holdLocally(run, item, delivered) {
    const issue = delivered.issues[item.key]?.url ?? `${item.key}'s new issue`;
    const upstreamNote = `backlogged upstream as ${issue}${delivered.pull ? `, added by ${delivered.pull}` : ''}; it is taken from there, never from the local queue`;
    const task = item.task ?? `the new task "${item.title}"`;
    if (!item.hold) {
        const reason = capitalised(upstreamNote);
        act(run, `hold ${task} for a deferral: ${reason}`, () => command('edi', ['hold', task, '--kind', 'deferral', '--reason', reason]));
        return reason;
    }
    if (item.hold.reason.includes(issue)) {
        return item.hold.reason;
    }
    const reason = `${item.hold.reason}; ${upstreamNote}`;
    act(run, `release ${task} and hold it again with its reason updated: ${reason}`, () => {
        command('edi', ['release', task]);
        command('edi', ['hold', task, '--kind', item.hold.kind, '--reason', reason, ...untilOf(item.hold)]);
    });
    return reason;
}

function capitalised(text) {
    return `${text[0].toUpperCase()}${text.slice(1)}`;
}

function untilOf(hold) {
    return /^\d{4}-\d{2}-\d{2}$/.test(hold.until ?? '') ? ['--until', hold.until] : [];
}

function printSummary(item, delivered, held) {
    console.log([
        '',
        item.key,
        `  doc:   ${itemPath(item.key)}`,
        `  pull:  ${delivered.pull ?? (delivered.unmerged ? 'a new pull request' : 'none needed, the item is on main')}`,
        `  issue: ${delivered.issues[item.key]?.url ?? 'a new issue'}`,
        `  hold:  ${item.task ?? 'the new task'}: ${held}`,
    ].join('\n'));
}

function git(directory, ...args) {
    return command('git', ['-C', directory, ...args]);
}

function gh(upstream, args, input) {
    return command('gh', [...args, '--repo', upstream.repository], input, upstream.gh);
}

function command(program, args, input, environment = {}) {
    try {
        return execFileSync(program, args, { encoding: 'utf8', input, env: { ...process.env, ...environment }, stdio: ['pipe', 'pipe', 'pipe'] });
    } catch (failure) {
        throw new Refusal(`${program} ${args.join(' ')} failed: ${(failure.stderr || failure.message).trim()}`);
    }
}

class ThrowawayCopy {

    constructor(run, upstream, base) {
        this.run = run;
        this.upstream = upstream;
        this.commits = 0;
        this.path = act(run, `make a throwaway copy of ${upstream.clone} at ${base}`, () => {
            const copy = join(mkdtempSync(join(tmpdir(), 'edi-backlog-item-')), PROJECT);
            git(upstream.clone, 'worktree', 'add', '--quiet', '--detach', copy, base);
            return copy;
        });
    }

    read(path) {
        return this.path ? readFileSync(join(this.path, path), 'utf8') : '';
    }

    write(path, text, action) {
        act(this.run, action, () => writeFileSync(join(this.path, path), text));
    }

    commit(subject) {
        act(this.run, `commit "${subject}"`, () => {
            git(this.path, 'add', '--all');
            git(this.path, 'commit', '--quiet', '--message', subject);
        });
        this.commits++;
    }

    push(branch) {
        act(this.run, `push ${branch}`, () => git(this.path, 'push', '--quiet', 'origin', `HEAD:refs/heads/${branch}`));
    }

    remove() {
        if (this.path) {
            git(this.upstream.clone, 'worktree', 'remove', '--force', this.path);
            rmSync(dirname(this.path), { recursive: true, force: true });
        }
    }
}

class BacklogIndex {

    constructor(text) {
        this.text = text;
        this.lines = text.split('\n');
    }

    section(name) {
        const sections = this.sections();
        const found = sections.filter(section => name !== '' && startsWithTheWords(section.heading, name));
        if (found.length !== 1) {
            throw new Refusal(`The section "${name}" names none or several of the index's sections: ${sections.map(section => section.heading).join('; ')}.`);
        }
        return found[0];
    }

    sections() {
        const sections = [];
        for (let at = this.lines.indexOf('## The index') + 1; at < this.lines.length && !this.lines[at].startsWith('## '); at++) {
            if (this.lines[at].startsWith('### ')) {
                sections.push(this.sectionAt(at));
            }
        }
        return sections;
    }

    sectionAt(headingLine) {
        let end = headingLine + 1;
        while (end < this.lines.length && !this.lines[end].startsWith('#')) {
            end++;
        }
        const table = [];
        for (let at = headingLine; at < end; at++) {
            if (this.lines[at].startsWith('|')) {
                table.push(at);
            }
        }
        const columns = cellsOf(this.lines[table[0]]);
        return {
            heading: this.lines[headingLine].slice(4),
            columns,
            ranksBySeverity: columns.includes('Severity'),
            rows: table.slice(2).map(at => this.lines[at]),
            lastRow: table.at(-1),
        };
    }

    names(key) {
        return this.rowOf(key) !== undefined;
    }

    rowOf(key) {
        return this.lines.find(line => line.includes(`| [${key}](${key}.md) |`));
    }

    withRow(item, takenElsewhere) {
        const section = this.section(item.section);
        const lines = [...this.lines];
        lines.splice(section.lastRow + 1, 0, rowFor(item, section, this.orderOfANewRowIn(section, takenElsewhere)));
        return new BacklogIndex(lines.join('\n'));
    }

    orderOfANewRowIn(section, takenElsewhere) {
        if (!section.rows.some(isNumbered)) {
            return '-';
        }
        return String(Math.max(...this.orders(), ...takenElsewhere) + 1);
    }

    orders() {
        return this.sections().flatMap(listed => listed.rows).filter(isNumbered).map(orderOf);
    }

    titleOf(key) {
        const row = this.rowOf(key);
        return row === undefined ? undefined : cellsOf(row)[this.sectionHolding(row).columns.indexOf('Title')];
    }

    sectionHolding(row) {
        return this.sections().find(section => section.rows.includes(row));
    }

    placeOf(key) {
        const row = this.rowOf(key);
        const order = cellsOf(row)[0];
        if (order === '-') {
            return 'no wave';
        }
        const heading = this.sectionHolding(row).heading;
        const wave = /^wave\s+(\d+)/i.exec(heading);
        return wave ? `wave ${wave[1]}, order ${order}` : `${heading.split(/\W/)[0].toLowerCase()} section, order ${order}`;
    }
}

function startsWithTheWords(heading, words) {
    return heading.toLowerCase().startsWith(words.toLowerCase()) && !/\w/.test(heading.charAt(words.length));
}

function rowFor(item, section, order) {
    const cells = {
        Order: order,
        Item: `[${item.key}](${item.key}.md)`,
        Title: item.title,
        Size: item.size,
        Severity: item.severity,
        'Blocked by': item.blockers.length === 0 ? '-' : item.blockers.join(', '),
    };
    return `| ${section.columns.map(column => cells[column]).join(' | ')} |`;
}

function isNumbered(row) {
    return /^\d+$/.test(cellsOf(row)[0]);
}

function orderOf(row) {
    return Number(cellsOf(row)[0]);
}

function cellsOf(row) {
    return row.split('|').slice(1, -1).map(cell => cell.trim());
}

main();
