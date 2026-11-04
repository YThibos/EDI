#!/usr/bin/env node
import {
    APPROVED, GitHub, Refusal, approverOf, captainOf, claimsOf, inPickupOrder, keyOf, openBlockersOf, plainTitleOf, refusalsReported, stateOf,
} from './backlog.mjs';

const USAGE = 'Usage: pickup.mjs [--next] [--repo OWNER/NAME] [--captain LOGIN]';

refusalsReported(() => print(optionsFrom(process.argv.slice(2))));

function print(options) {
    const github = GitHub.ofTheCheckout(options.repo);
    const captain = options.captain ?? captainOf(github);
    const listed = backlogIssuesOf(github);
    const blockers = openBlockersByIssue(github, listed);
    const issues = inPickupOrder(listed, blockers);
    const claims = claimsOf(github.all('pulls?state=open'));
    console.log(options.next ? nextOf(github, captain, issues, claims) : boardOf(issues, blockers, claims));
}

function optionsFrom(args) {
    const options = { next: false };
    for (let at = 0; at < args.length; at++) {
        if (args[at] === '--next') {
            options.next = true;
        } else if (['--repo', '--captain'].includes(args[at]) && args[at + 1] && !args[at + 1].startsWith('-')) {
            options[args[at].slice(2)] = args[++at];
        } else {
            throw new Refusal(USAGE);
        }
    }
    return options;
}

function backlogIssuesOf(github) {
    return github.all('issues?state=open').filter(issue => !issue.pull_request && keyOf(issue.title));
}

function openBlockersByIssue(github, issues) {
    const blocked = issues.filter(issue => openBlockersOf(issue) > 0);
    return Object.fromEntries(blocked.map(issue => [issue.number,
        github.all(`issues/${issue.number}/dependencies/blocked_by`).filter(blocker => blocker.state === 'open').map(blocker => blocker.number)]));
}

function nextOf(github, captain, issues, claims) {
    const next = issues
        .filter(issue => stateOf(issue) === 'open')
        .filter(issue => openBlockersOf(issue) === 0)
        .filter(issue => claims[issue.number] === undefined)
        .filter(issue => approvedByTheCaptain(github, captain, issue));
    if (next.length === 0) {
        return 'No open issue may be taken: none is approved by the captain, cloud-ok, unheld, unclaimed and free of open blockers.';
    }
    return next.map(issue => `#${issue.number} ${issue.title} (${milestoneOf(issue)}) ${issue.html_url}`).join('\n');
}

function approvedByTheCaptain(github, captain, issue) {
    const approver = approverOf(github.all(`issues/${issue.number}/events`));
    if (approver !== captain) {
        console.error(`#${issue.number} is skipped: its ${APPROVED} label was added by ${approver ?? 'nobody still on it'}, not by the captain's account ${captain}.`);
    }
    return approver === captain;
}

function milestoneOf(issue) {
    if (!issue.milestone) {
        return 'no milestone';
    }
    return issue.milestone.due_on ? `${issue.milestone.title}, due ${issue.milestone.due_on.slice(0, 10)}` : issue.milestone.title;
}

function boardOf(issues, blockers, claims) {
    const header = ['Issue', 'Key', 'Title', 'Milestone', 'State', 'Blocked by', 'Claimed by'];
    const rows = issues.map(issue => [
        `#${issue.number}`,
        keyOf(issue.title),
        plainTitleOf(issue.title),
        milestoneOf(issue),
        stateOf(issue),
        (blockers[issue.number] ?? []).map(number => `#${number}`).join(', ') || '-',
        claims[issue.number] ? `#${claims[issue.number]}` : '-',
    ]);
    return [header, header.map(() => '---'), ...rows].map(cells => `| ${cells.join(' | ')} |`).join('\n');
}
