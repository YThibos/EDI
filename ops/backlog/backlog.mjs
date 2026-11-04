import { execFileSync } from 'node:child_process';

export const PRIMARY_MARKER = '[EDI primary]';
export const APPROVED = 'captain-approved';
export const CLOUD = 'cloud-ok';
export const MACHINE = 'needs-captain-machine';
export const HELD = 'held';
export const SECURITY = 'security';
export const LABEL_DESCRIPTIONS = {
    [APPROVED]: "Approved on the captain's word; only the captain's account adds it",
    [CLOUD]: 'A cloud session may take it',
    [MACHINE]: "Needs the captain's machine",
    [HELD]: "Held until the captain's word; the reason is on the issue",
    [SECURITY]: 'A security finding',
};
export const SPEC_SECTIONS = ['Goal', 'Context', 'Acceptance criteria', 'Requirements from decisions and answers', 'Read first', 'How to verify'];
export const OPTIONAL_SECTIONS = ['Research and measurements'];
export const HEADER_FIELDS = ['Kind', 'Source', 'Size'];

const PAGE = 100;
const KEYED_TITLE = /^(EDI-\d+) (.+)$/;
const CLOSING = /\b(?:close[sd]?|fix(?:e[sd])?|resolve[sd]?)\s+#(\d+)\b/gi;

export class Refusal extends Error {}

export function refusalsReported(work) {
    try {
        work();
    } catch (failure) {
        if (!(failure instanceof Refusal)) {
            throw failure;
        }
        console.error(failure.message);
        process.exitCode = 1;
    }
}

export class GitHub {

    constructor(repository, environment = {}) {
        this.repository = repository;
        this.environment = environment;
    }

    static ofTheCheckout(named, directory = process.cwd()) {
        return new GitHub(named ?? process.env.GH_REPO ?? repositoryOfTheRemote(directory));
    }

    get(path) {
        return this.call('GET', path);
    }

    all(path) {
        const listed = [];
        for (let page = 1; ; page++) {
            const answer = this.get(`${path}${path.includes('?') ? '&' : '?'}per_page=${PAGE}&page=${page}`);
            listed.push(...answer);
            if (answer.length < PAGE) {
                return listed;
            }
        }
    }

    post(path, fields) {
        return this.call('POST', path, fields);
    }

    patch(path, fields) {
        return this.call('PATCH', path, fields);
    }

    delete(path) {
        return this.call('DELETE', path);
    }

    call(method, path, fields) {
        const route = `repos/${this.repository}/${path}`.replace(/\/$/, '');
        const args = ['api', '--method', method, route, ...(fields === undefined ? [] : ['--input', '-'])];
        const answer = command('gh', args, fields === undefined ? undefined : JSON.stringify(fields), this.environment);
        return answer.trim() === '' ? null : JSON.parse(answer);
    }
}

export function repositoryOfTheRemote(directory) {
    const origin = command('git', ['-C', directory, 'remote', 'get-url', 'origin']).trim();
    const path = /([^/:]+)\/([^/]+?)(?:\.git)?\/?$/.exec(origin);
    if (!path) {
        throw new Refusal(`The origin ${origin} names no owner and repository; pass --repo OWNER/NAME.`);
    }
    return `${path[1]}/${path[2]}`;
}

export function command(program, args, input, environment = {}) {
    try {
        return execFileSync(program, args, { encoding: 'utf8', input, maxBuffer: 256 * 1024 * 1024, env: { ...process.env, ...environment }, stdio: ['pipe', 'pipe', 'pipe'] });
    } catch (failure) {
        throw new Refusal(`${program} ${args.join(' ')} failed: ${(failure.stderr || failure.message).trim()}`);
    }
}

export function captainOf(github) {
    const owner = github.get('').owner;
    if (owner.type !== 'User') {
        throw new Refusal(`${github.repository} belongs to the organisation ${owner.login}, so the captain's account cannot be read from it; pass --captain LOGIN.`);
    }
    return owner.login;
}

export function keyOf(title) {
    return KEYED_TITLE.exec(title)?.[1];
}

export function plainTitleOf(title) {
    return KEYED_TITLE.exec(title)?.[2] ?? title;
}

export function labelsOf(issue) {
    return issue.labels.map(label => (typeof label === 'string' ? label : label.name));
}

export function approverOf(events) {
    let approver = null;
    for (const event of events) {
        if (event.label?.name !== APPROVED) {
            continue;
        }
        if (event.event === 'labeled') {
            approver = event.actor?.login ?? null;
        } else if (event.event === 'unlabeled') {
            approver = null;
        }
    }
    return approver;
}

export function stateOf(issue) {
    const labels = labelsOf(issue);
    if (labels.includes(HELD)) {
        return 'held';
    }
    if (labels.includes(MACHINE)) {
        return 'needs the captain\'s machine';
    }
    if (!labels.includes(APPROVED)) {
        return 'not approved';
    }
    return labels.includes(CLOUD) ? 'open' : 'not cloud-ok';
}

export function openBlockersOf(issue) {
    return issue.issue_dependencies_summary?.blocked_by ?? 0;
}

export function inPickupOrder(issues, blockersOf = {}) {
    const listed = new Set(issues.map(issue => issue.number));
    const waiting = new Map(issues.map(issue => [issue.number, new Set((blockersOf[issue.number] ?? []).filter(number => listed.has(number)))]));
    const ordered = [];
    let left = [...issues].sort(byDueDateThenNumber);
    while (left.length > 0) {
        const free = left.find(issue => waiting.get(issue.number).size === 0) ?? left[0];
        ordered.push(free);
        left = left.filter(issue => issue !== free);
        waiting.forEach(blockers => blockers.delete(free.number));
    }
    return ordered;
}

function byDueDateThenNumber(one, other) {
    return dueOf(one) - dueOf(other) || one.number - other.number;
}

function dueOf(issue) {
    if (!issue.milestone) {
        return Number.MAX_SAFE_INTEGER;
    }
    return issue.milestone.due_on ? Date.parse(issue.milestone.due_on) : Number.MAX_SAFE_INTEGER - 1;
}

export function claimsOf(pulls) {
    const claims = {};
    for (const pull of pulls) {
        for (const closing of `${pull.title}\n${pull.body ?? ''}`.matchAll(CLOSING)) {
            claims[closing[1]] ??= pull.number;
        }
    }
    return claims;
}

export function sectionsOf(body) {
    let fenced = false;
    const sections = [];
    for (const line of body.split('\n')) {
        fenced = /^\s*(```|~~~)/.test(line) ? !fenced : fenced;
        if (!fenced && line.startsWith('## ')) {
            sections.push(line.slice(3).trim());
        }
    }
    return sections;
}

export function missingSectionsOf(body) {
    const present = sectionsOf(body);
    return SPEC_SECTIONS.filter(section => !present.includes(section));
}
