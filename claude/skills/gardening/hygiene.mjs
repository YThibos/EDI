import { execFileSync } from 'node:child_process';
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { basename, join } from 'node:path';

import { MINUTE, Refusal, iso } from '../introspect/evidence.mjs';

const DAY = 1440 * MINUTE;
const HELD_DAYS = 14;
const UNTOUCHED_DAYS = 28;
const MEMORY_SHARE = 0.9;
const CLOSED_STAGES = ['retired', 'dropped'];
const MERGED_STAGES = ['merged', 'landed'];
const WHAT_LIFTS_A_HOLD = /https?:\/\/|\bD\d+\b|\b[A-Z][A-Z0-9]+-\d+\b/;
const BRANCH = /^(feature|chore|research|fix|bugfix)\/(.+)$/;
const KEY = /\b[A-Z][A-Z0-9]+-\d+\b/;
const KEYS = new RegExp(KEY.source, 'g');
const STORY_KEY = /^key:\s*([A-Z][A-Z0-9]+-\d+)\s*$/m;
const STORY_STATUS = /\*\*Status:\*\*\s*([^(*\n]+)/;
const DONE_STATUS = /^(done|closed|resolved)$/i;
const CODE_NAME = /`([A-Z][a-z0-9]+(?:[A-Z][a-z0-9]*)+)`/g;
const SECTIONS_NAMING_THE_PAST = /^## (Alternatives rejected|How we got here)$/;
const CODE_FILES = ['*.java', '*.ts', '*.mjs', '*.js', '*.sql', '*.sh', '*.kt', '*.py'];
const CONFIGURATION_FILES = ['*.json', '*.yml', '*.yaml'];

export function hygieneOf(home, store, facts, upstream) {
    const tasks = tasksIn(store);
    const clones = clonesIn(home);
    return [
        ...longHolds(store, facts.now),
        ...orphanedWorktrees(home, tasks),
        ...orphanedBranches(clones, tasks),
        ...untouchedBacklogItems(upstream, facts.now),
        ...storyFilesDisagreeing(clones, facts),
        ...todosOnClosedTickets(clones, upstream, facts),
        ...decisionRecordsNamingRemovedCode(clones),
        ...memoryOverBudget(store),
    ];
}

function tasksIn(store) {
    return store.prepare('select task.id, task.stage, task.work_key, dispatch.branch from task left join dispatch on dispatch.task_id = task.id').all();
}

function clonesIn(home) {
    const projects = join(home, 'projects');
    return directoriesIn(projects).filter(clone => existsSync(join(clone, '.git')));
}

function directoriesIn(parent) {
    return existsSync(parent) ? readdirSync(parent).sort().map(name => join(parent, name)).filter(path => statSync(path).isDirectory()) : [];
}

function longHolds(store, now) {
    return store.prepare('select task_id, reason, until, set_at from captain_hold').all()
        .filter(hold => hold.until === null && now - Date.parse(hold.set_at) > HELD_DAYS * DAY && !WHAT_LIFTS_A_HOLD.test(hold.reason))
        .map(hold => finding('long-hold', hold.task_id, false,
            `${hold.task_id} has been held since ${iso(Date.parse(hold.set_at)).slice(0, 10)} with no date and nothing named that lifts the hold: "${hold.reason}"`));
}

function finding(kind, subject, standing, text) {
    return { kind, subject, standing, text };
}

function orphanedWorktrees(home, tasks) {
    const byId = new Map(tasks.map(task => [task.id, task]));
    return directoriesIn(join(home, 'worktrees')).flatMap(directoriesIn)
        .map(copy => ({ copy: copy.split('/').slice(-2).join('/'), task: byId.get(basename(copy)) }))
        .filter(({ task }) => !task || isClosed(task) || hasMerged(task))
        .map(({ copy, task }) => task && hasMerged(task) ? copyACleanupRetires(task) : copyOfNoOpenTask(copy, task));
}

function isClosed(task) {
    return CLOSED_STAGES.includes(task.stage);
}

function hasMerged(task) {
    return MERGED_STAGES.includes(task.stage);
}

function copyACleanupRetires(task) {
    return finding('orphaned-worktree', task.id, true, `${task.id} is ${task.stage} and its isolated copy is still there: edi cleanup ${task.id} retires it`);
}

function copyOfNoOpenTask(copy, task) {
    return finding('orphaned-worktree', copy, false, `the isolated copy ${copy} belongs to ${task ? `a ${task.stage} task` : 'no task'}`);
}

function orphanedBranches(clones, tasks) {
    const open = tasks.filter(task => !isClosed(task));
    const branches = new Set(open.map(task => task.branch).filter(branch => branch));
    const keys = new Set(open.map(task => task.work_key).filter(key => key));
    return clones.flatMap(clone => localBranchesOf(clone)
        .filter(branch => BRANCH.test(branch) && !branches.has(branch) && !keys.has(BRANCH.exec(branch)[2]))
        .map(branch => finding('orphaned-branch', `${projectOf(clone)} ${branch}`, false,
            `the branch ${branch} of ${projectOf(clone)} belongs to no open task`)));
}

function localBranchesOf(clone) {
    return git(clone, ['for-each-ref', '--format=%(refname:short)', 'refs/heads/']).split('\n').filter(branch => branch !== '');
}

function git(clone, args) {
    try {
        return execFileSync('git', ['-C', clone, ...args], { encoding: 'utf8', maxBuffer: 256 * 1024 * 1024, stdio: ['ignore', 'pipe', 'pipe'] });
    } catch (failure) {
        if (failure.status === 1 && failure.stderr === '') {
            return '';
        }
        throw new Refusal(`git -C ${clone} ${args.join(' ')} failed: ${String(failure.stderr || failure.message).trim()}`);
    }
}

function projectOf(clone) {
    return basename(clone);
}

function untouchedBacklogItems(upstream, now) {
    return upstream.issues.filter(issue => issue.state === 'open' && !issue.pull_request && issue.updated_at)
        .filter(issue => now - Date.parse(issue.updated_at) > UNTOUCHED_DAYS * DAY)
        .map(issue => finding('untouched-backlog-item', `#${issue.number}`, false,
            `${issue.title} has not changed since ${issue.updated_at.slice(0, 10)}: ${issue.html_url}`));
}

function storyFilesDisagreeing(clones, facts) {
    const tracked = trackerStatuses(facts);
    return clones.flatMap(clone => filesHolding(clone, '^key: [A-Z][A-Z0-9]+-[0-9]+$', ['*.md'])
        .map(path => ({ path, text: readFileSync(join(clone, path), 'utf8') }))
        .map(story => ({ ...story, key: STORY_KEY.exec(story.text)?.[1], status: STORY_STATUS.exec(story.text)?.[1].trim() }))
        .filter(story => story.key && story.status && tracked.has(story.key) && !sameStatus(story.status, tracked.get(story.key)))
        .map(story => finding('story-file-disagrees', `${projectOf(clone)} ${story.path}`, false,
            `${story.path} of ${projectOf(clone)} says ${story.key} is "${story.status}" where the tracker last moved it to "${tracked.get(story.key)}"`)));
}

function trackerStatuses(facts) {
    const statuses = new Map();
    facts.events.filter(event => event.type === 'TicketTransitioned' && event.at <= facts.now).forEach(event => statuses.set(event.payload.key, event.payload.to));
    return statuses;
}

function filesHolding(clone, pattern, paths) {
    return git(clone, ['grep', '-l', '-I', '-E', pattern, '--', ...paths]).split('\n').filter(path => path !== '');
}

function sameStatus(one, other) {
    return one.trim().toLowerCase() === other.trim().toLowerCase();
}

function todosOnClosedTickets(clones, upstream, facts) {
    const closed = closedTickets(upstream, facts);
    return clones.flatMap(clone => git(clone, ['grep', '-n', '-I', '-E', '(TODO|FIXME).*[A-Z][A-Z0-9]+-[0-9]+']).split('\n').filter(line => line !== '')
        .map(line => ({ line, keys: [...line.slice(line.indexOf(':', line.indexOf(':') + 1)).matchAll(KEYS)].map(match => match[0]) }))
        .flatMap(({ line, keys }) => keys.filter(key => closed.has(key)).map(key => ({ place: line.split(':').slice(0, 2).join(':'), key })))
        .map(({ place, key }) => finding('todo-on-a-closed-ticket', `${projectOf(clone)} ${place}`, false,
            `${place} of ${projectOf(clone)} carries a TODO or FIXME for ${key}, which is closed`)));
}

function closedTickets(upstream, facts) {
    const closed = new Set(upstream.issues.filter(issue => issue.state === 'closed').map(issue => KEY.exec(issue.title)?.[0] ?? null).filter(key => key !== null));
    trackerStatuses(facts).forEach((status, key) => DONE_STATUS.test(status) && closed.add(key));
    return closed;
}

function decisionRecordsNamingRemovedCode(clones) {
    return clones.filter(clone => existsSync(join(clone, 'docs', 'decisions'))).flatMap(clone => {
        const records = decisionRecordsOf(clone);
        const removed = namesRemovedFromTheCode(clone, [...new Set(records.flatMap(record => record.names))]);
        return records.map(record => ({ file: record.file, removed: record.names.filter(name => removed.has(name)) }))
            .filter(record => record.removed.length > 0)
            .map(record => finding('decision-names-removed-code', `${projectOf(clone)} docs/decisions/${record.file}`, false,
                `docs/decisions/${record.file} of ${projectOf(clone)} names ${record.removed.join(', ')}, which the code once carried and no longer does`));
    });
}

function decisionRecordsOf(clone) {
    return readdirSync(join(clone, 'docs', 'decisions')).filter(file => file.endsWith('.md') && file !== 'README.md')
        .map(file => ({ file, names: [...new Set(namesInForce(readFileSync(join(clone, 'docs', 'decisions', file), 'utf8')))] }));
}

function namesRemovedFromTheCode(clone, names) {
    const carried = namesInTheCode(clone);
    const absent = names.filter(name => !carried.has(name));
    if (absent.length === 0) {
        return new Set();
    }
    const history = git(clone, ['log', '--format=', '-p', '-U0', '-G', `(${absent.join('|')})`, '--', ...CODE_FILES]);
    return new Set(absent.filter(name => new RegExp(`\\b${name}\\b`).test(history)));
}

function namesInTheCode(clone) {
    return new Set(git(clone, ['grep', '-h', '-o', '-I', '-E', '\\b[A-Z][a-z0-9]+([A-Z][a-z0-9]*)+\\b', '--', ...CODE_FILES, ...CONFIGURATION_FILES]).split('\n'));
}

function namesInForce(record) {
    const inForce = [];
    let naming = true;
    for (const line of record.split('\n')) {
        if (line.startsWith('## ')) {
            naming = !SECTIONS_NAMING_THE_PAST.test(line);
        } else if (naming) {
            inForce.push(...[...line.matchAll(CODE_NAME)].map(match => match[1]));
        }
    }
    return inForce;
}

function memoryOverBudget(store) {
    const check = memoryOf(store);
    if (!check || check.tokens <= check.budget * MEMORY_SHARE) {
        return [];
    }
    return [finding('memory-budget', 'startup memory', true,
        `the startup memory holds ${check.tokens} of its ${check.budget} tokens, past ${MEMORY_SHARE * 100}% of its budget: /stow curates it`)];
}

export function memoryOf(store) {
    const check = store.prepare('select tokens, budget, checked_at from memory_check order by checked_at desc limit 1').get();
    return check ? { tokens: check.tokens, budget: check.budget, checkedAt: check.checked_at } : null;
}
