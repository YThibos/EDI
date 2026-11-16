import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { homedir } from 'node:os';
import { join } from 'node:path';

const ROUTINES = {
    'wake loop (edi wait and reading its output)': /^edi wait$|^cat \/tmp\/\S+\/tasks\/\S+\.output/,
    'turn report rendering': /edi report/,
    "captain's words recorded": /edi captain said|edi decision answer/,
    'merge request state checks': /glab (mr|api).*merge_requests|gh pr (view|list)/,
    'tracker bookkeeping (worklog, transition, sprint)': /jira-sync\.sh (worklog-add|status|transition|sprint-add)/,
    'worker inspection (list, peek, task show)': /edi worker (list|peek)|edi task show/,
    'steering and notes': /edi steer|edi task note/,
    'SNAPSHOT and worked-line checks': /snapshot|worked:/i,
};
const SIGNALS = { 'stop-hook blocks': 'Stop hook feedback', 'captain-word refusals': 'did not type' };
const NOT_TYPED = /^(<|Stop hook feedback|\[Request interrupted|Caveat:)/;
const LEADING_CD = /^(cd [^;&]+(&&|;)\s*)+/;
const CHAIN = /\s*(?:&&|\|\||;)\s*/;
const COMMAND_WORD = /^[a-z][a-z.]*(-[a-z]+)?$/;
const HEAD_WORDS = 3;
const SEQUENCE_LENGTHS = [2, 3];
const REPEATED = 5;

export function transcriptsOf(home) {
    return join(homedir(), '.claude', 'projects', home.replace(/[^A-Za-z0-9]/g, '-'));
}

export function conversationIn(directory, window) {
    const conversation = { captain: [], primary: [], calls: [], signals: Object.fromEntries(Object.keys(SIGNALS).map(name => [name, 0])) };
    const seen = new Set();
    for (const entry of entriesIn(directory, window.since)) {
        const at = Date.parse(entry.timestamp ?? '');
        if (!(at >= window.since && at < window.until) || seen.has(entry.uuid)) {
            continue;
        }
        seen.add(entry.uuid);
        take(conversation, entry, at);
    }
    return conversation;
}

function entriesIn(directory, since) {
    if (!existsSync(directory)) {
        return [];
    }
    return readdirSync(directory)
        .filter(name => name.endsWith('.jsonl'))
        .map(name => join(directory, name))
        .filter(path => statSync(path).mtimeMs >= since)
        .flatMap(path => readFileSync(path, 'utf8').split('\n').map(entryOf).filter(entry => entry !== null)
            .map(entry => ({ ...entry, session: entry.sessionId ?? path })));
}

function entryOf(line) {
    try {
        return line.trim() === '' ? null : JSON.parse(line);
    } catch {
        return null;
    }
}

function take(conversation, entry, at) {
    if (entry.type === 'user') {
        countSignals(conversation.signals, entry);
        const typed = captainsWordsIn(entry);
        if (typed !== null) {
            conversation.captain.push({ at, session: entry.session, text: typed });
        }
    } else if (entry.type === 'assistant') {
        for (const block of contentOf(entry)) {
            if (block.type === 'text') {
                conversation.primary.push({ at, session: entry.session, text: block.text });
            } else if (block.type === 'tool_use') {
                conversation.calls.push({ at, session: entry.session, tool: block.name, command: commandOf(block) });
            }
        }
    }
}

function countSignals(signals, entry) {
    const written = JSON.stringify(entry.message ?? '');
    for (const [name, words] of Object.entries(SIGNALS)) {
        if (written.includes(words)) {
            signals[name]++;
        }
    }
}

function captainsWordsIn(entry) {
    if (entry.isMeta || entry.isCompactSummary || (entry.origin && entry.origin.kind !== 'human')) {
        return null;
    }
    const content = entry.message?.content;
    if (Array.isArray(content) && content.some(block => block.type === 'tool_result')) {
        return null;
    }
    const text = (typeof content === 'string' ? content : contentOf(entry).filter(block => block.type === 'text').map(block => block.text).join('\n')).trim();
    return text === '' || NOT_TYPED.test(text) ? null : text;
}

function contentOf(entry) {
    const content = entry.message?.content;
    return Array.isArray(content) ? content : [];
}

function commandOf(block) {
    return block.name === 'Bash' ? String(block.input?.command ?? '').trim().replace(LEADING_CD, '') : null;
}

export function routinesOf(conversation) {
    const counted = Object.fromEntries(Object.keys(ROUTINES).map(name => [name, 0]));
    for (const call of conversation.calls.filter(call => call.command !== null)) {
        const routine = Object.entries(ROUTINES).find(([, pattern]) => pattern.test(call.command));
        if (routine) {
            counted[routine[0]]++;
        }
    }
    return {
        sessions: new Set(conversation.calls.map(call => call.session)).size,
        toolCalls: conversation.calls.length,
        routines: counted,
        signals: conversation.signals,
    };
}

export function handSequencesOf(conversation) {
    const counted = new Map();
    const count = sequence => counted.set(sequence.join(' && '), { sequence, count: (counted.get(sequence.join(' && '))?.count ?? 0) + 1 });
    for (const heads of commandHeadsBySession(conversation)) {
        heads.filter(chain => chain.length > 1).forEach(count);
        SEQUENCE_LENGTHS.forEach(length => heads.slice(0, heads.length - length + 1)
            .map((_, start) => heads.slice(start, start + length).flat())
            .forEach(count));
    }
    const repeated = [...counted.values()].filter(found => found.count >= REPEATED && new Set(found.sequence).size > 1);
    return repeated.filter(found => !insideALongerOne(found, repeated)).sort((one, other) => other.count - one.count || one.sequence.length - other.sequence.length);
}

function commandHeadsBySession(conversation) {
    const bySession = new Map();
    for (const call of conversation.calls.filter(call => call.command)) {
        const chain = call.command.split(CHAIN).map(headOf).filter(head => head !== '');
        if (chain.length > 0) {
            bySession.set(call.session, [...(bySession.get(call.session) ?? []), chain]);
        }
    }
    return [...bySession.values()];
}

function headOf(command) {
    const words = command.trim().split(/\s+/);
    const head = [];
    for (const word of words) {
        if (head.length === HEAD_WORDS || !COMMAND_WORD.test(word)) {
            break;
        }
        head.push(word);
    }
    return head.join(' ');
}

function insideALongerOne(found, repeated) {
    const written = ` ${found.sequence.join(' && ')} `;
    return repeated.some(other => other !== found && other.sequence.length > found.sequence.length && other.count >= found.count
        && ` ${other.sequence.join(' && ')} `.includes(written));
}
