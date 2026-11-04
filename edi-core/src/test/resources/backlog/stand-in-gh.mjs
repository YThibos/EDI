import { appendFileSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const STATE = process.env.STATE;
const WORLD = join(STATE, 'github.json');
const args = process.argv.slice(2);
const world = JSON.parse(readFileSync(WORLD, 'utf8'));
world.issues ??= [];
world.pulls ??= [];
world.labels ??= [];
world.milestones ??= [];
world.events ??= {};
world.comments ??= {};
world.blockedBy ??= {};
world.subIssues ??= {};

appendFileSync(join(STATE, 'calls'), `gh ${args.join(' ')}\n`);
if (args[0] !== 'api') {
    fail(`the stand-in answers gh api only, not gh ${args.join(' ')}`);
}
const method = args[args.indexOf('--method') + 1] ?? 'GET';
const route = args.find(arg => arg.startsWith('repos/'));
const input = args.includes('--input') ? JSON.parse(readFileSync(0, 'utf8') || '{}') : undefined;
const [path, query] = route.replace(/^repos\/[^/]+\/[^/]+\/?/, '').split('?');
const parameters = new URLSearchParams(query ?? '');
const answer = answered(method, path.split('/').filter(part => part !== ''), input);
writeFileSync(WORLD, JSON.stringify(world, null, 2));
if (answer !== undefined) {
    process.stdout.write(JSON.stringify(answer));
}

function answered(verb, parts, body) {
    const [collection, id, child, grandchild] = parts;
    if (parts.length === 0) {
        return { full_name: 'example/edi', owner: world.owner };
    }
    if (collection === 'issues' && id === undefined) {
        return verb === 'POST' ? shown(opened(body)) : paged(world.issues.filter(stateMatches).map(shown));
    }
    if (collection === 'issues' && child === undefined) {
        return verb === 'PATCH' ? shown(patched(issue(id), body)) : shown(issue(id));
    }
    if (collection === 'issues' && child === 'labels') {
        return verb === 'DELETE' ? unlabelled(issue(id), decodeURIComponent(grandchild)) : labelled(issue(id), body.labels);
    }
    if (collection === 'issues' && child === 'events') {
        return paged(world.events[id] ?? []);
    }
    if (collection === 'issues' && child === 'comments') {
        if (verb === 'POST') {
            const comment = { id: Date.now(), user: { login: actor() }, body: body.body };
            (world.comments[id] ??= []).push(comment);
            return comment;
        }
        return paged(world.comments[id] ?? []);
    }
    if (collection === 'issues' && child === 'dependencies') {
        if (verb === 'POST') {
            const blocker = world.issues.find(candidate => candidate.id === body.issue_id) ?? fail(`no issue has the id ${body.issue_id}`);
            (world.blockedBy[id] ??= []).push(blocker.number);
            return shown(issue(id));
        }
        return paged((world.blockedBy[id] ?? []).map(number => shown(issue(number))));
    }
    if (collection === 'issues' && child === 'sub_issues') {
        if (verb === 'POST') {
            const sub = world.issues.find(candidate => candidate.id === body.sub_issue_id) ?? fail(`no issue has the id ${body.sub_issue_id}`);
            (world.subIssues[id] ??= []).push(sub.number);
            return shown(issue(id));
        }
        return paged((world.subIssues[id] ?? []).map(number => shown(issue(number))));
    }
    if (collection === 'pulls') {
        return paged(world.pulls);
    }
    if (collection === 'labels') {
        return labelsAnswered(verb, id === undefined ? undefined : decodeURIComponent(id), body);
    }
    if (collection === 'milestones') {
        return milestonesAnswered(verb, id, body);
    }
    return fail(`the stand-in has no route ${verb} ${parts.join('/')}`);
}

function stateMatches(candidate) {
    const state = parameters.get('state') ?? 'open';
    return state === 'all' || candidate.state === state;
}

function paged(list) {
    const size = Number(parameters.get('per_page') ?? 30);
    const page = Number(parameters.get('page') ?? 1);
    return list.slice((page - 1) * size, page * size);
}

function issue(number) {
    return world.issues.find(candidate => candidate.number === Number(number)) ?? fail(`no issue #${number}`);
}

function shown(stored) {
    const blockers = (world.blockedBy[stored.number] ?? []).map(issue);
    const milestone = world.milestones.find(candidate => candidate.number === stored.milestone) ?? null;
    return {
        ...stored,
        html_url: `https://github.com/example/edi/issues/${stored.number}`,
        labels: stored.labels.map(name => ({ name })),
        milestone,
        user: { login: stored.author ?? world.owner.login },
        issue_dependencies_summary: { blocked_by: blockers.filter(blocker => blocker.state === 'open').length, total_blocked_by: blockers.length },
    };
}

function opened(body) {
    const number = Math.max(0, ...world.issues.map(candidate => candidate.number), ...world.pulls.map(pull => pull.number)) + 1;
    const created = { number, id: 1000 + number, title: body.title, body: body.body ?? '', state: 'open', labels: [], milestone: body.milestone ?? null };
    world.issues.push(created);
    labelled(created, body.labels ?? []);
    return created;
}

function patched(stored, body) {
    for (const field of ['title', 'body', 'state', 'state_reason', 'milestone']) {
        if (body[field] !== undefined) {
            stored[field] = body[field];
        }
    }
    return stored;
}

function labelled(stored, names) {
    for (const name of names) {
        if (!world.labels.some(label => label.name === name)) {
            world.labels.push({ name, description: '' });
        }
        if (!stored.labels.includes(name)) {
            stored.labels.push(name);
            (world.events[stored.number] ??= []).push({ event: 'labeled', actor: { login: actor() }, label: { name } });
        }
    }
    return stored.labels.map(name => ({ name }));
}

function unlabelled(stored, name) {
    stored.labels = stored.labels.filter(label => label !== name);
    (world.events[stored.number] ??= []).push({ event: 'unlabeled', actor: { login: actor() }, label: { name } });
    return stored.labels.map(label => ({ name: label }));
}

function actor() {
    return process.env.STAND_IN_ACTOR ?? world.owner.login;
}

function labelsAnswered(verb, name, body) {
    if (verb === 'GET') {
        return paged(world.labels);
    }
    if (verb === 'POST') {
        world.labels.push({ name: body.name, description: body.description ?? '' });
        return body;
    }
    const label = world.labels.find(candidate => candidate.name === name) ?? fail(`no label ${name}`);
    if (verb === 'DELETE') {
        world.labels = world.labels.filter(candidate => candidate !== label);
        world.issues.forEach(stored => { stored.labels = stored.labels.filter(candidate => candidate !== name); });
        return undefined;
    }
    if (body.new_name) {
        world.issues.forEach(stored => { stored.labels = stored.labels.map(candidate => (candidate === name ? body.new_name : candidate)); });
        label.name = body.new_name;
    }
    label.description = body.description ?? label.description;
    return label;
}

function milestonesAnswered(verb, number, body) {
    if (verb === 'GET') {
        return paged(world.milestones);
    }
    if (verb === 'POST') {
        const created = { number: world.milestones.length + 1, title: body.title, due_on: body.due_on ?? null, description: body.description ?? '', state: 'open' };
        world.milestones.push(created);
        return created;
    }
    const milestone = world.milestones.find(candidate => candidate.number === Number(number)) ?? fail(`no milestone ${number}`);
    Object.assign(milestone, body);
    return milestone;
}

function fail(message) {
    process.stderr.write(`${message}\n`);
    process.exit(1);
}
