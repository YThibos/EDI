import { readFileSync } from 'node:fs';
import { Refusal } from '../backlog/backlog.mjs';

const FIELDS = ['advisory', 'reason', 'accepted', 'reviewed'];
const ADVISORY_ID = /^GHSA(-[2-9cfghjmpqrvwx]{4}){3}$/;
const DAY = /^(\d{4})-(\d{2})-(\d{2})$/;

export function exceptionsIn(file) {
    const document = documentIn(file);
    const exceptions = document.exceptions;
    if (!Array.isArray(exceptions) || Object.keys(document).some(key => key !== 'exceptions')) {
        throw new Refusal(`${file} must hold one object with an "exceptions" list and nothing else.`);
    }
    return exceptions;
}

export function problemsOfTheExceptions(exceptions) {
    return exceptions.flatMap((exception, index) => problemsOfOne(exception, index + 1));
}

export function wellFormed(exception) {
    return problemsOfOne(exception, 0).length === 0;
}

export function reviewHasPassed(exception, today) {
    return exception.reviewed < today;
}

function documentIn(file) {
    try {
        return JSON.parse(readFileSync(file, 'utf8'));
    } catch (failure) {
        throw new Refusal(`${file} cannot be read as JSON: ${failure.message}`);
    }
}

function problemsOfOne(exception, position) {
    const named = `Exception ${position}`;
    if (exception === null || typeof exception !== 'object' || Array.isArray(exception)) {
        return [`${named} must be an object with ${FIELDS.join(', ')}.`];
    }
    return [
        ...Object.keys(exception).filter(field => !FIELDS.includes(field)).map(field => `${named} has the field ${field}, which excepts nothing; an exception names one advisory.`),
        ...FIELDS.filter(field => !present(exception[field])).map(field => `${named} lacks its ${field}.`),
        ...problemsOfTheValues(exception, named),
    ];
}

function problemsOfTheValues(exception, named) {
    const problems = [];
    if (present(exception.advisory) && !ADVISORY_ID.test(exception.advisory)) {
        problems.push(`${named} names ${exception.advisory}, which is not one GHSA id.`);
    }
    for (const field of ['accepted', 'reviewed']) {
        if (present(exception[field]) && !isDay(exception[field])) {
            problems.push(`${named} has ${field} ${exception[field]}, which is not a date written YYYY-MM-DD.`);
        }
    }
    return problems;
}

function present(value) {
    return typeof value === 'string' && value.trim() !== '';
}

function isDay(value) {
    const parts = typeof value === 'string' ? DAY.exec(value) : null;
    return parts !== null && new Date(`${value}T00:00:00Z`).toISOString().startsWith(value);
}
