#!/usr/bin/env node
import { readFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { Refusal, command, refusalsReported } from '../backlog/backlog.mjs';
import { exceptionsIn, problemsOfTheExceptions, reviewHasPassed, wellFormed } from './exceptions.mjs';

const USAGE = 'Usage: npm-audit-check.mjs [--exceptions FILE] [--report FILE] [--today YYYY-MM-DD]';
const BLOCKING = ['high', 'critical'];
const ADVISORY_URL = /\/advisories\/(GHSA-[^/?#]+)$/;

refusalsReported(() => check(optionsFrom(process.argv.slice(2))));

function check(options) {
    const advisories = advisoriesOf(auditReportFrom(options.report));
    const exceptions = exceptionsIn(options.exceptions);
    const problems = problemsOfTheExceptions(exceptions);
    const inForce = exceptions.filter(wellFormed);
    const ended = inForce.flatMap(exception => reasonsEnded(exception, advisories, options.today));
    printApplied(exceptionsApplied(inForce, ended), advisories);
    finish([...problems, ...ended.map(reason => reason.text), ...advisoriesUnexcepted(advisories, inForce).map(unexceptedText)]);
}

function optionsFrom(args) {
    const options = { exceptions: 'npm-audit-exceptions.json', today: new Date().toISOString().slice(0, 10) };
    for (let at = 0; at < args.length; at += 2) {
        const name = args[at]?.replace(/^--/, '');
        if (!['exceptions', 'report', 'today'].includes(name) || !args[at + 1] || args[at + 1].startsWith('-')) {
            throw new Refusal(USAGE);
        }
        options[name] = args[at + 1];
    }
    return options;
}

function auditReportFrom(file) {
    const answer = file === undefined ? npmAudit() : readFileSync(file, 'utf8');
    let report;
    try {
        report = JSON.parse(answer);
    } catch (failure) {
        throw new Refusal(`npm audit printed no JSON report: ${answer.slice(0, 200)}`);
    }
    if (report.error || typeof report.vulnerabilities !== 'object') {
        throw new Refusal(`npm audit gave no report: ${report.error?.summary ?? JSON.stringify(report).slice(0, 200)}`);
    }
    return report;
}

function npmAudit() {
    const run = spawnSync('npm', ['audit', '--json'], { encoding: 'utf8', maxBuffer: 256 * 1024 * 1024 });
    if (run.error) {
        throw new Refusal(`npm audit could not run: ${run.error.message}`);
    }
    return run.stdout;
}

function advisoriesOf(report) {
    const advisories = new Map();
    for (const vulnerability of Object.values(report.vulnerabilities)) {
        for (const via of vulnerability.via.filter(cause => typeof cause === 'object')) {
            const id = advisoryIdOf(via);
            advisories.set(id, { id, package: via.name, severity: via.severity, title: via.title });
        }
    }
    return advisories;
}

function advisoryIdOf(via) {
    return ADVISORY_URL.exec(via.url)?.[1] ?? via.url;
}

function reasonsEnded(exception, advisories, today) {
    const named = `Remove the exception for ${exception.advisory}`;
    const advisory = advisories.get(exception.advisory);
    if (advisory === undefined) {
        return [{ exception, text: `${named}: the advisory no longer applies to the installed tree.` }];
    }
    if (!blocks(advisory)) {
        return [{ exception, text: `${named}: the advisory no longer reaches the high level, it is ${advisory.severity}.` }];
    }
    return [
        ...(reviewHasPassed(exception, today) ? [{ exception, text: `${named}: its review date ${exception.reviewed} has passed.` }] : []),
        ...patchedVersionsOf(advisory).map(version => ({ exception, text: `${named}: the advisory has a patched version, ${version}.` })),
    ];
}

function blocks(advisory) {
    return BLOCKING.includes(advisory.severity);
}

function patchedVersionsOf(advisory) {
    const published = JSON.parse(command('gh', ['api', `advisories/${advisory.id}`]));
    return published.vulnerabilities
        .filter(vulnerable => vulnerable.package?.name === advisory.package && vulnerable.first_patched_version)
        .map(vulnerable => vulnerable.first_patched_version);
}

function exceptionsApplied(inForce, ended) {
    return inForce.filter(exception => !ended.some(reason => reason.exception === exception));
}

function printApplied(applied, advisories) {
    for (const exception of applied) {
        const advisory = advisories.get(exception.advisory);
        console.log(`Excepted ${exception.advisory} (${advisory.package}, ${advisory.severity}): ${exception.reason} - reviewed again on ${exception.reviewed}.`);
    }
}

function advisoriesUnexcepted(advisories, inForce) {
    return [...advisories.values()]
        .filter(blocks)
        .filter(advisory => !inForce.some(exception => exception.advisory === advisory.id));
}

function unexceptedText(advisory) {
    return `${advisory.id} (${advisory.package}, ${advisory.severity}) is not excepted: ${advisory.title}.`;
}

function finish(problems) {
    if (problems.length > 0) {
        throw new Refusal(problems.join('\n'));
    }
    console.log('No high or critical advisory is left unexcepted.');
}
