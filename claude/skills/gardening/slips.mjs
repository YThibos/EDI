import { MINUTE, clock, questionsLeftOpen, safe } from '../introspect/evidence.mjs';

const THRESHOLDS = { questionMinutes: 10, gateMinutes: 30, changingMinutes: 60, reviewRounds: 5 };

const WEIGHTS = {
    'captain-disagrees': 5, 'merged-against-a-warning': 5, 'defect-after-merge': 5,
    'captain-corrects': 4, 'primary-admits': 4,
    'question-left-open': 3, 'gate-parked': 3, 'request-changing': 3, 'steer-unacknowledged': 3, 'review-rounds': 3, 'captain-questions': 3,
    'decision-overridden': 2, 'captain-repeats': 2, 'captains-word-refused': 2,
    'safety-check-refused': 1,
};
const CAPTAINS_SIGNALS = [
    { kind: 'captain-disagrees', pattern: /\(!\)/ },
    { kind: 'captain-corrects', pattern: /\b(you misunderstood|why is|I didn'?t see|I said|wait, I meant|I told you|I asked (you )?before|restate)\b/i },
    { kind: 'captain-questions', pattern: /\(\?\)/ },
    { kind: 'captain-repeats', pattern: /\bagain\b/i },
];
const ADMISSION = /\b(my slip|my mistake|my fault|my error|that'?s on me|I missed|I misread|I overlooked|I should have|I was wrong|a confession)\b/i;
const FIX = /\b(fix|defect|bug|regression|broken|repair)/i;
const CAPTAINS_WORD_RULE = 'authority.captains-word';
const SAFETY_CHECK = /^(guard|publish)\./;
const QUOTE_WIDTH = 240;

export function slipsIn(conversation, facts, store) {
    return [
        ...captainsSignals(conversation),
        ...admissions(conversation),
        ...questionsLeftOpen(facts, THRESHOLDS.questionMinutes, 'question').filter(slip => facts.inTheWindow(slip.at)),
        ...questionsLeftOpen(facts, THRESHOLDS.gateMinutes, 'gate').filter(slip => facts.inTheWindow(slip.at)),
        ...decisionsOverridden(facts, store),
        ...mergesAgainstAWarning(facts),
        ...requestsChangingTooLong(facts),
        ...refusals(facts, store),
        ...steersNeverAcknowledged(facts),
        ...requestsWithManyReviewRounds(facts),
        ...defectsAfterAMerge(facts, store),
    ].map(weighed).sort((one, other) => other.weight - one.weight || one.at - other.at);
}

function weighed(slip) {
    return { ...slip, weight: WEIGHTS[slip.kind] };
}

export function captainsSignalOf(text) {
    return CAPTAINS_SIGNALS.find(signal => signal.pattern.test(text)) ?? null;
}

function captainsSignals(conversation) {
    return conversation.captain
        .map(message => ({ message, signal: captainsSignalOf(message.text) }))
        .filter(found => found.signal !== null)
        .map(({ message, signal }) => ({
            kind: signal.kind, at: message.at, task: null,
            text: `the captain wrote: ${quoted(message.text)}`,
        }));
}

function admissions(conversation) {
    return conversation.primary
        .filter(said => ADMISSION.test(said.text))
        .map(said => ({ kind: 'primary-admits', at: said.at, task: null, text: `the primary wrote: ${quoted(around(said.text, ADMISSION))}` }));
}

function quoted(text) {
    const flat = text.replace(/\s+/g, ' ').trim();
    return `"${flat.length > QUOTE_WIDTH ? flat.slice(0, QUOTE_WIDTH - 3) + '...' : flat}"`;
}

function around(text, pattern) {
    const found = pattern.exec(text);
    return text.slice(Math.max(0, found.index - QUOTE_WIDTH / 2), found.index + QUOTE_WIDTH / 2);
}

function decisionsOverridden(facts, store) {
    return facts.events.filter(event => event.type === 'DecisionAnswered' && facts.inTheWindow(event.at))
        .filter(event => !optionsOf(store, event.payload.number).includes(event.payload.option))
        .map(event => ({
            kind: 'decision-overridden', at: event.at, task: event.task_id,
            text: `decision ${event.payload.number} was answered with a note that none of its options carries: ${quoted(event.payload.verbatim ?? '')}`,
        }));
}

function optionsOf(store, number) {
    return store.prepare('select option_key from decision_option where decision_number = ?').all(Number(String(number).replace(/^D/, '')))
        .map(row => row.option_key);
}

function mergesAgainstAWarning(facts) {
    return changingSpans(facts).filter(span => span.endedBy === 'DraftMerged' && facts.inTheWindow(span.to))
        .map(span => ({
            kind: 'merged-against-a-warning', at: span.to, task: span.task,
            text: `${span.task} was merged while it was changing, ${minutes(span.to - span.from)} minutes after its worker worked again past its done`,
        }));
}

function changingSpans(facts) {
    const spans = [];
    const open = new Map();
    const delivered = new Set();
    for (const event of facts.events) {
        const task = event.task_id;
        if (event.type === 'StatusReported' && event.actor.startsWith('worker:')) {
            if (event.payload.state === 'done') {
                delivered.add(task);
                close(open, spans, task, event);
            } else if (delivered.has(task) && !open.has(task)) {
                open.set(task, event.at);
            }
        } else if (['DraftMerged', 'TaskCleanedUp', 'TaskDropped'].includes(event.type)) {
            delivered.delete(task);
            close(open, spans, task, event);
        }
    }
    open.forEach((from, task) => spans.push({ task, from, to: facts.now, endedBy: null }));
    return spans;
}

function close(open, spans, task, event) {
    if (open.has(task)) {
        spans.push({ task, from: open.get(task), to: event.at, endedBy: event.type === 'StatusReported' ? 'done' : event.type });
        open.delete(task);
    }
}

function requestsChangingTooLong(facts) {
    return changingSpans(facts)
        .filter(span => span.to - span.from > THRESHOLDS.changingMinutes * MINUTE && (facts.inTheWindow(span.from) || facts.inTheWindow(span.to)))
        .map(span => ({
            kind: 'request-changing', at: span.from, task: span.task,
            text: `${span.task} was changing for ${minutes(span.to - span.from)} minutes after its done${span.endedBy ? '' : ' and still is'}, past the`
                + ` ${THRESHOLDS.changingMinutes}-minute threshold`,
        }));
}

function refusals(facts, store) {
    const counted = new Map();
    for (const firing of firingsIn(store, facts).filter(firing => firing.rule === CAPTAINS_WORD_RULE || SAFETY_CHECK.test(firing.rule))) {
        const found = counted.get(firing.rule) ?? { kind: refusalKindOf(firing.rule), rule: firing.rule, at: firing.at, firings: [] };
        counted.set(firing.rule, { ...found, firings: [...found.firings, firing] });
    }
    return [...counted.values()].map(found => ({
        kind: found.kind, at: found.at, task: null,
        text: `${found.rule} refused ${found.firings.length} time${found.firings.length === 1 ? '' : 's'}`,
        evidence: found.firings.map(firingLine),
    }));
}

function firingsIn(store, facts) {
    const recorded = store.prepare("select at, rule, outcome, actor, task_id, reason from rule_fired where outcome = 'refused' order by number").all()
        .map(row => ({ ...row, at: Date.parse(row.at) }))
        .filter(firing => facts.inTheWindow(firing.at));
    if (recorded.length > 0) {
        return recorded;
    }
    return facts.events.filter(event => event.type === 'ToolCallRefused' && facts.inTheWindow(event.at))
        .map(event => ({ at: event.at, rule: `guard.${event.payload.rule}`, outcome: 'refused', actor: event.actor, task_id: event.task_id, reason: event.payload.rule }));
}

function refusalKindOf(rule) {
    return rule === CAPTAINS_WORD_RULE ? 'captains-word-refused' : 'safety-check-refused';
}

function firingLine(firing) {
    return `${clock(firing.at)} ${firing.actor}${firing.task_id ? ' on ' + firing.task_id : ''} was refused: ${safe(firing.reason, { full: false })}`;
}

function steersNeverAcknowledged(facts) {
    const acknowledged = new Set(facts.events.filter(event => event.type === 'SteerAcknowledged').map(event => `${event.task_id} ${event.payload.number}`));
    return facts.events.filter(event => event.type === 'SteerSent' && facts.inTheWindow(event.at))
        .filter(event => !acknowledged.has(`${event.task_id} ${event.payload.number}`))
        .map(event => ({
            kind: 'steer-unacknowledged', at: event.at, task: event.task_id,
            text: `steer ${event.payload.number} to ${event.task_id} was never acknowledged`,
        }));
}

function requestsWithManyReviewRounds(facts) {
    return mergedRequests(facts).filter(request => request.rounds >= THRESHOLDS.reviewRounds)
        .map(request => ({
            kind: 'review-rounds', at: request.mergedAt, task: request.task,
            text: `${request.task} took ${request.rounds} review rounds before its merge, at or past the threshold of ${THRESHOLDS.reviewRounds}`,
        }));
}

export function mergedRequests(facts) {
    return facts.events.filter(event => event.type === 'DraftMerged' && facts.inTheWindow(event.at))
        .map(merge => ({
            task: merge.task_id,
            mergedAt: merge.at,
            rounds: reviewRoundsOf(facts, merge.task_id),
            doneAt: facts.events.find(event => event.task_id === merge.task_id && event.type === 'StatusReported' && event.payload.state === 'done')?.at ?? null,
        }));
}

function reviewRoundsOf(facts, task) {
    const counted = facts.events.filter(event => event.task_id === task && event.type === 'ReviewCounted' && Number.isInteger(event.payload.rounds)).at(-1);
    return counted ? counted.payload.rounds : new Set(facts.runs.filter(run => run.task_id === task).map(run => run.run_id)).size;
}

export function defectsAfterAMerge(facts, store) {
    return [...commitsLeftBehind(facts), ...fixesOfMergedWork(facts, store)];
}

function commitsLeftBehind(facts) {
    return facts.events.filter(event => ['StageChanged', 'StageDerived'].includes(event.type) && facts.inTheWindow(event.at))
        .filter(event => (event.payload.notLanded ?? []).length > 0)
        .map(event => ({
            kind: 'defect-after-merge', at: event.at, task: event.task_id,
            text: `${event.task_id} was merged with ${event.payload.notLanded.length} commit${event.payload.notLanded.length === 1 ? '' : 's'} left out of what landed`,
        }));
}

function fixesOfMergedWork(facts, store) {
    const mergedKeys = new Map();
    const found = [];
    for (const event of facts.events) {
        if (event.type === 'DraftMerged') {
            const key = workKeyOf(store, event.task_id);
            if (key) {
                mergedKeys.set(key, event.task_id);
            }
        } else if (event.type === 'TaskAdded' && mergedKeys.has(event.payload.workKey) && FIX.test(event.payload.title ?? '') && facts.inTheWindow(event.at)) {
            found.push({
                kind: 'defect-after-merge', at: event.at, task: event.task_id,
                text: `${event.task_id} fixes ${event.payload.workKey} after ${mergedKeys.get(event.payload.workKey)} merged: ${quoted(event.payload.title)}`,
            });
        }
    }
    return found;
}

function workKeyOf(store, task) {
    return store.prepare('select work_key from task where id = ?').get(task)?.work_key ?? null;
}

function minutes(span) {
    return Math.floor(span / MINUTE);
}
