const OUTCOME_NAMES = {
    captainsCorrections: "The captain's corrections",
    captainsDisagreements: 'The captain\'s "(!)" markers',
    captainsQuestions: 'The captain\'s "(?)" markers',
    questionToAnswerMinutes: 'Question to answer, median minutes',
    reviewRoundsPerRequest: 'Review rounds per request',
    doneToLandedMinutes: 'Done to landed, median minutes',
    defectsAfterMerge: 'Defects found after a merge',
};
const SLIP_ANSWERS = [['approve', 'Approve the draft as a hard rule'], ['adjust', 'Adjust it as my note says'], ['reject', 'Reject it']];
const RULE_ANSWERS = [['keep', 'Keep'], ['retire', 'Retire'], ['prove', 'Prove with a test']];
const HYGIENE_ANSWERS = [['leave', 'Leave it'], ['do', 'Do it']];
const SPARK_ANSWERS = [['open', 'Leave open'], ['backlog', 'Backlog'], ['fold', 'Fold into an item'], ['drop', 'Drop']];
const STYLE = `
:root { color-scheme: dark; --void: #060a17; --panel: #0d1630; --panel-raised: #111d3d; --line: #1c2a52; --text-hi: #eaf0ff; --text: #b9c6ea;
  --text-dim: #8391bd; --ion: #56c8ff; --ion-soft: rgba(86, 200, 255, 0.12); --amber: #ffb84d; --amber-soft: rgba(255, 184, 77, 0.12);
  --green: #3ee0a1; --red: #ff6479; --font-display: "Exo 2", "Segoe UI", system-ui, sans-serif; --font-sans: "Inter", "Segoe UI", system-ui, sans-serif;
  --font-mono: "JetBrains Mono", "Cascadia Code", ui-monospace, monospace; }
* { box-sizing: border-box; }
body { margin: 0; background: var(--void); color: var(--text); font: 15px/1.5 var(--font-sans); }
main { max-width: 1100px; margin: 0 auto; padding: 32px 24px 64px; }
h1, h2, h3 { font-family: var(--font-display); color: var(--text-hi); margin: 0; }
h1 { font-size: 1.7rem; } h2 { font-size: 1.2rem; margin: 36px 0 12px; } h3 { font-size: 1rem; }
.lede { color: var(--text-dim); margin: 6px 0 0; }
section > p.empty { color: var(--text-dim); }
.panel { background: var(--panel); border: 1px solid var(--line); border-radius: 12px; padding: 16px 18px; margin: 12px 0; min-width: 0; }
.slip { border-left: 3px solid var(--amber); }
.kind { font-family: var(--font-mono); font-size: 0.78rem; color: var(--ion); }
.when { font-family: var(--font-mono); font-size: 0.78rem; color: var(--text-dim); }
table { width: 100%; border-collapse: collapse; font-size: 0.86rem; }
th, td { text-align: left; padding: 6px 8px; border-bottom: 1px solid var(--line); vertical-align: top; overflow-wrap: anywhere; }
th { color: var(--text-dim); font-weight: 500; }
td.number { font-family: var(--font-mono); text-align: right; white-space: nowrap; }
.better { color: var(--green); } .worse { color: var(--red); }
pre { background: var(--void); border: 1px solid var(--line); border-radius: 8px; padding: 10px; font: 0.78rem/1.45 var(--font-mono); white-space: pre-wrap;
  overflow-wrap: anywhere; margin: 8px 0; }
details > summary { cursor: pointer; color: var(--text-dim); }
form { margin-top: 12px; display: grid; gap: 8px; }
fieldset { border: 0; padding: 0; margin: 0; display: flex; flex-wrap: wrap; gap: 6px 18px; }
label { cursor: pointer; }
select, textarea, input[type=text] { background: var(--panel-raised); color: var(--text-hi); border: 1px solid var(--line); border-radius: 6px; padding: 6px 8px;
  font: inherit; min-width: 0; max-width: 100%; }
textarea { width: 100%; min-height: 3.2em; }
button { justify-self: start; background: var(--ion-soft); color: var(--ion); border: 1px solid var(--ion); border-radius: 6px; padding: 6px 14px;
  font: 600 0.86rem var(--font-sans); cursor: pointer; }
.standing { border-left: 3px solid var(--green); }
.word { border-left: 3px solid var(--amber); }
@media (max-width: 760px) { main { padding: 20px 16px 48px; } }
`;

export function boardOf(round, drafts) {
    const questions = [];
    const asked = key => questions.push(key) && key;
    const sections = [
        weekSection(round),
        slipsSection(round, drafts, asked),
        rulesSection(round, asked),
        handSequencesSection(round),
        hygieneSection(round, asked),
        sparksSection(round, asked),
    ];
    return { html: page(round, sections), questions };
}

function page(round, sections) {
    return `<!doctype html>
<html lang="en-GB" data-theme="dark" data-lavish-annotate="off">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="color-scheme" content="dark">
<meta name="darkreader-lock">
<title>Gardening round, week of ${escaped(round.week.monday)}</title>
<style>${STYLE}</style>
</head>
<body>
<main>
<h1>Gardening round, week of ${escaped(round.week.monday)}</h1>
<p class="lede">The week from ${escaped(round.week.from)} to ${escaped(round.week.until)} against the week of ${escaped(round.previousWeek.monday)}.
Nothing here becomes a rule without your word: approve, adjust or reject each draft, and queue each answer.</p>
${sections.join('\n')}
</main>
</body>
</html>
`;
}

function weekSection(round) {
    const outcomes = Object.entries(OUTCOME_NAMES).map(([name, label]) =>
        row([label, number(round.outcomes.previous[name]), number(round.outcomes.week[name]), trend(round.outcomes.week[name], round.outcomes.previous[name])], [1, 2, 3]));
    const routines = Object.keys(round.routines.week.routines).map(name =>
        row([name, number(round.routines.previous.routines[name]), number(round.routines.week.routines[name]), trend(round.routines.week.routines[name], round.routines.previous.routines[name])], [1, 2, 3]));
    return `<section>
<h2>The week against the previous</h2>
<div class="panel"><table>
<tr><th>Outcome</th><th>Previous</th><th>This week</th><th>Change</th></tr>
${outcomes.join('\n')}
</table></div>
<details class="panel"><summary>Routine calls behind them: ${number(round.routines.week.toolCalls)} tool calls in ${number(round.routines.week.sessions)} sessions,
against ${number(round.routines.previous.toolCalls)} in ${number(round.routines.previous.sessions)}</summary>
<table>
<tr><th>Routine</th><th>Previous</th><th>This week</th><th>Change</th></tr>
${routines.join('\n')}
</table></details>
</section>`;
}

function row(cells, numbers = []) {
    return `<tr>${cells.map((cell, position) => `<td${numbers.includes(position) ? ' class="number"' : ''}>${cell}</td>`).join('')}</tr>`;
}

function number(value) {
    return value === null || value === undefined ? '-' : escaped(String(value));
}

function trend(now, before) {
    if (typeof now !== 'number' || typeof before !== 'number' || now === before) {
        return '-';
    }
    const change = Math.round((now - before) * 10) / 10;
    return `<span class="${change < 0 ? 'better' : 'worse'}">${change > 0 ? '+' : ''}${change}</span>`;
}

function slipsSection(round, drafts, asked) {
    if (round.slips.length === 0) {
        return '<section><h2>Slips</h2><p class="empty">No slip was found this week.</p></section>';
    }
    const top = round.top.map(number => round.slips[number - 1])
        .map((slip, position) => topSlip(slip, drafts[position], asked(`slip-${position + 1}`), position + 1));
    const rest = round.slips.filter(slip => !round.top.includes(slip.number));
    return `<section>
<h2>The top three slips, each with a hard rule drafted</h2>
${top.join('\n')}
${rest.length === 0 ? '' : `<details class="panel"><summary>The other ${rest.length} slips the round found</summary>
<table>
<tr><th>Slip</th><th>When</th><th>Kind</th><th>What</th></tr>
${rest.map(slip => row([number(slip.number), `<span class="when">${escaped(slip.at)}</span>`, `<span class="kind">${escaped(slip.kind)}</span>`, escaped(slip.text)])).join('\n')}
</table></details>`}
</section>`;
}

function topSlip(slip, draft, question, position) {
    return `<article class="panel slip">
<p class="kind">${escaped(slip.kind)}${slip.task ? ' · ' + escaped(slip.task) : ''} <span class="when">${escaped(slip.at)}</span></p>
<h3>${position}. ${escaped(draft?.fields.title ?? slip.text)}</h3>
<p>${escaped(slip.text)}</p>
${slip.evidence.length === 0 ? '' : `<pre>${slip.evidence.map(escaped).join('\n')}</pre>`}
${draft ? draftSummary(draft) : ''}
<form data-lavish-question="${question}" onsubmit="${submitted(question, `'Slip ${position}: ' + answer + (note ? ', ' + note : '')`,
        `{ question: '${question}', answer: answer, note: note, draft: '${draft?.file ?? ''}' }`)}">
<fieldset>${SLIP_ANSWERS.map(([value, label]) => `<label><input type="radio" name="answer" value="${value}"> ${label}</label>`).join('')}</fieldset>
<textarea name="note" placeholder="What to adjust, or why to reject"></textarea>
<button type="submit">Queue this answer</button>
</form>
</article>`;
}

function draftSummary(draft) {
    return `<details><summary>The drafted backlog item: ${escaped(draft.file)}</summary>
<h3>Goal</h3><pre>${escaped(draft.sections.Goal ?? '')}</pre>
<h3>Acceptance criteria</h3><pre>${escaped(draft.sections['Acceptance criteria'] ?? '')}</pre>
</details>`;
}

function submitted(question, text, data) {
    return escaped(`event.preventDefault(); const form = new FormData(event.currentTarget); const answer = form.get('answer'); const note = (form.get('note') || '').trim();`
        + ` if (answer) window.lavish.queuePrompt(${text}, { tag: 'choice', text: ${text}, element: event.currentTarget, data: ${data} });`);
}

function rulesSection(round, asked) {
    const candidates = [
        ...round.rules.neverFired.map(rule => ({ ...rule, why: `never fired${round.rules.recordingSince ? ' since ' + round.rules.recordingSince.slice(0, 10) : ''}` })),
        ...round.rules.fired.filter(rule => round.rules.often.includes(rule.rule)).map(rule => ({ ...rule, why: `fired ${rule.total} times, ${rule.previous} the week before` })),
    ];
    const fired = round.rules.fired.map(rule => row([`<span class="kind">${escaped(rule.rule)}</span>`, number(rule.refused), number(rule.warned), number(rule.allowed),
        number(rule.previous), `<span class="when">${escaped(rule.last)}</span>`], [1, 2, 3, 4]));
    return `<section>
<h2>Rules to retire or prove with a test</h2>
${round.rules.recordingSince === null ? '<p class="empty">No firing was recorded before the week ended.</p>' : ''}
${round.rules.fired.length === 0 ? '<p class="empty">No rule fired this week.</p>' : `<details class="panel"><summary>${round.rules.fired.length} rules fired this week</summary>
<table><tr><th>Rule</th><th>Refused</th><th>Warned</th><th>Allowed</th><th>Previous week</th><th>Last</th></tr>
${fired.join('\n')}</table></details>`}
${candidates.length === 0 ? '<p class="empty">No rule is a candidate this week.</p>' : batch(asked('rules'), 'Rules',
        candidates.map(rule => ({ id: rule.rule, label: `${rule.rule}: ${rule.protects ?? ''} (${rule.why})` })), RULE_ANSWERS, false)}
</section>`;
}

function batch(question, title, items, answers, withNote) {
    const choices = items.map((item, position) => `<tr><td><span class="kind">${escaped(item.id)}</span><br>${escaped(item.label)}</td>
<td><select name="item-${position}" data-id="${escaped(item.id)}" data-label="${escaped(item.label)}">${answers.map(([value, label]) => `<option value="${value}">${label}</option>`).join('')}</select>
${withNote ? `<input type="text" name="note-${position}" placeholder="Key or reason" aria-label="Key or reason for ${escaped(item.id)}">` : ''}</td></tr>`);
    const script = `event.preventDefault(); const items = [...event.currentTarget.querySelectorAll('select')].map((select) => ({ id: select.dataset.id,`
        + ` label: select.dataset.label, disposition: select.value, note: (select.parentElement.querySelector('input')?.value || '').trim() }));`
        + ` window.lavish.queuePrompt('${title}: act on every disposition and account for every id.', { tag: 'tracked-batch', text: '${title}: ' + items.length`
        + ` + ' items', element: event.currentTarget, data: { question: '${question}', items: items } });`;
    return `<form class="panel" data-lavish-question="${question}" onsubmit="${escaped(script)}">
<table>${choices.join('\n')}</table>
<button type="submit">Queue these answers</button>
</form>`;
}

function handSequencesSection(round) {
    return `<section>
<h2>Repeated hand sequences</h2>
${round.handSequences.length === 0 ? '<p class="empty">No sequence of commands was chained five times or more.</p>' : `<div class="panel"><table>
<tr><th>Sequence</th><th>Times</th></tr>
${round.handSequences.map(found => row([`<code>${escaped(found.sequence.join(' → '))}</code>`, number(found.count)], [1])).join('\n')}
</table></div>`}
</section>`;
}

function hygieneSection(round, asked) {
    const standing = round.hygiene.filter(found => found.standing);
    const needingTheWord = round.hygiene.filter(found => !found.standing);
    return `<section>
<h2>Hygiene</h2>
<div class="panel standing"><h3>Done under standing rules</h3>
${standing.length === 0 ? '<p class="empty">Nothing this week.</p>' : `<ul>${standing.map(found => `<li>${escaped(found.text)}</li>`).join('')}</ul>`}
</div>
<div class="panel word"><h3>Needing your word</h3>
${needingTheWord.length === 0 ? '<p class="empty">Nothing this week.</p>' : batch(asked('hygiene'), 'Hygiene',
        needingTheWord.map(found => ({ id: `${found.kind} ${found.subject}`, label: found.text })), HYGIENE_ANSWERS, true)}
</div>
</section>`;
}

function sparksSection(round, asked) {
    return `<section>
<h2>The spark inbox</h2>
${round.sparks.length === 0 ? '<p class="empty">No spark is open.</p>' : batch(asked('sparks'), 'Sparks',
        round.sparks.map(spark => ({ id: `spark ${spark.number}`, label: `${spark.caughtAt.slice(0, 16).replace('T', ' ')}, ${spark.author}${spark.focus ? ', on ' + spark.focus : ''}: ${spark.text}` })),
        SPARK_ANSWERS, true)}
</section>`;
}

function escaped(text) {
    return String(text).replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&#39;');
}
