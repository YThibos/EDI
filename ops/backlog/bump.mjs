#!/usr/bin/env node
import { readFileSync } from 'node:fs';

import { GitHub, PRIMARY_MARKER, Refusal, SESSION_MARKER, nextHigherPriorityOf, priorityLabelsOf, priorityOf, refusalsReported } from './backlog.mjs';

const USAGE = 'Usage: bump.mjs [--primary] [--repo OWNER/NAME] ISSUE < finding';

refusalsReported(() => bump(optionsFrom(process.argv.slice(2))));

function bump(options) {
    const finding = findingFrom(readFileSync(0, 'utf8'));
    const github = upstreamOf(options);
    const issue = bumpableIssue(github, options.issue);
    const raised = raisedPriorityOf(issue);
    const current = priorityOf(issue);
    relabel(github, issue, raised);
    comment(github, issue, options.primary ? PRIMARY_MARKER : SESSION_MARKER, `Raised from ${current.name} to ${raised.name}.`, finding);
    console.log(`#${issue.number} raised from ${current.name} to ${raised.name}.`);
}

function optionsFrom(args) {
    const options = { primary: false };
    for (let at = 0; at < args.length; at++) {
        if (args[at] === '--primary') {
            options.primary = true;
        } else if (args[at] === '--repo' && args[at + 1] && !args[at + 1].startsWith('-')) {
            options.repo = args[++at];
        } else if (/^\d+$/.test(args[at]) && options.issue === undefined) {
            options.issue = Number(args[at]);
        } else {
            throw new Refusal(USAGE);
        }
    }
    if (options.issue === undefined) {
        throw new Refusal(USAGE);
    }
    return options;
}

function findingFrom(text) {
    if (text.trim() === '') {
        throw new Refusal('The finding that caused the bump is given on standard input, and it is empty.');
    }
    return text.trim();
}

function upstreamOf(options) {
    return options.repo ? new GitHub(options.repo) : GitHub.ofTheEdiClone();
}

function bumpableIssue(github, number) {
    const issue = github.get(`issues/${number}`);
    if (issue.pull_request) {
        throw new Refusal(`#${number} is a pull request, not a backlog issue.`);
    }
    if (issue.state !== 'open') {
        throw new Refusal(`#${number} is closed; only an open issue is raised.`);
    }
    return issue;
}

function raisedPriorityOf(issue) {
    const raised = nextHigherPriorityOf(issue);
    if (!raised) {
        throw new Refusal(`#${issue.number} is already at the highest priority, ${priorityOf(issue).name}; nothing is raised and nothing is commented.`);
    }
    return raised;
}

function relabel(github, issue, raised) {
    github.post(`issues/${issue.number}/labels`, { labels: [raised.name] });
    for (const held of priorityLabelsOf(issue).filter(name => name !== raised.name)) {
        github.delete(`issues/${issue.number}/labels/${encodeURIComponent(held)}`);
    }
}

function comment(github, issue, marker, headline, finding) {
    try {
        github.post(`issues/${issue.number}/comments`, { body: `${marker} ${headline}\n\n${finding}` });
    } catch (failure) {
        throw new Refusal(`#${issue.number} is raised, but the finding was not posted: ${failure.message}\nPost the finding on #${issue.number} by hand; bumping it again would raise it a second level.`);
    }
}
