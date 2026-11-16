import { MINUTE, GATE_KEY, questionsOf } from '../introspect/evidence.mjs';

import { captainsSignalOf, defectsAfterAMerge, mergedRequests } from './slips.mjs';

const CORRECTIONS = ['captain-corrects', 'captain-repeats'];

export function outcomesOf(conversation, facts, store) {
    const merged = mergedRequests(facts);
    return {
        captainsCorrections: conversation.captain.filter(message => CORRECTIONS.includes(captainsSignalOf(withoutMarkers(message.text))?.kind)).length,
        captainsDisagreements: conversation.captain.filter(message => message.text.includes('(!)')).length,
        captainsQuestions: conversation.captain.filter(message => message.text.includes('(?)')).length,
        questionToAnswerMinutes: median(answeredQuestionWaits(facts)),
        reviewRoundsPerRequest: mean(merged.map(request => request.rounds)),
        doneToLandedMinutes: median(merged.filter(request => request.doneAt !== null).map(request => (request.mergedAt - request.doneAt) / MINUTE)),
        defectsAfterMerge: defectsAfterAMerge(facts, store).length,
    };
}

function withoutMarkers(text) {
    return text.replaceAll('(!)', '').replaceAll('(?)', '');
}

function answeredQuestionWaits(facts) {
    return questionsOf(facts)
        .filter(question => !GATE_KEY.test(question.key) && question.answeredAt !== null && facts.inTheWindow(question.at))
        .map(question => (question.answeredAt - question.at) / MINUTE);
}

function median(values) {
    if (values.length === 0) {
        return null;
    }
    const sorted = [...values].sort((one, other) => one - other);
    const middle = Math.floor(sorted.length / 2);
    return rounded(sorted.length % 2 === 1 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2);
}

function mean(values) {
    return values.length === 0 ? null : rounded(values.reduce((sum, value) => sum + value, 0) / values.length);
}

function rounded(value) {
    return Math.round(value * 10) / 10;
}
