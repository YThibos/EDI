#!/usr/bin/env node
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const USAGE = 'Usage: board.mjs [--next]';
const BACKLOG = dirname(fileURLToPath(import.meta.url));
const ROW = /^\| [^|]+ \| \[(EDI-\d+)\]\(EDI-\d+\.md\) \|/;
const KEY = /EDI-\d+/g;

class Refusal extends Error {}

try {
    print(process.argv.slice(2));
} catch (failure) {
    if (!(failure instanceof Refusal)) {
        throw failure;
    }
    console.error(failure.message);
    process.exitCode = 1;
}

function print(args) {
    if (args.length > 1 || (args.length === 1 && args[0] !== '--next')) {
        throw new Refusal(USAGE);
    }
    const sections = sectionsOf(readFileSync(join(BACKLOG, 'README.md'), 'utf8'));
    console.log(args[0] === '--next' ? nextOf(sections) : boardOf(sections));
}

function sectionsOf(index) {
    const lines = index.split('\n');
    const start = lines.indexOf('## The index');
    if (start < 0) {
        throw new Refusal('The backlog README has no "## The index" section.');
    }
    const sections = [];
    for (const line of lines.slice(start + 1)) {
        if (line.startsWith('## ')) {
            break;
        }
        if (line.startsWith('### ')) {
            sections.push({ heading: line.slice(4), columns: [], items: [] });
        } else if (line.startsWith('| Order ') && sections.length > 0) {
            sections.at(-1).columns = cellsOf(line);
        } else if (ROW.test(line) && sections.length > 0) {
            sections.at(-1).items.push(itemOf(sections.at(-1).columns, line));
        }
    }
    return sections;
}

function itemOf(columns, row) {
    const cells = Object.fromEntries(cellsOf(row).map((cell, position) => [columns[position], cell]));
    const key = ROW.exec(row)[1];
    const fields = fieldsOf(key);
    return {
        key,
        order: cells.Order,
        title: cells.Title,
        size: cells.Size,
        severity: cells.Severity,
        blockedBy: cells['Blocked by'],
        status: requiredField(key, fields, 'Status'),
        issue: fields.Issue ?? '-',
    };
}

function fieldsOf(key) {
    let text;
    try {
        text = readFileSync(join(BACKLOG, `${key}.md`), 'utf8');
    } catch {
        throw new Refusal(`The index names ${key}, but ${key}.md does not exist.`);
    }
    const fields = {};
    for (const match of text.matchAll(/^\| (Status|Issue) \| (.*) \|$/gm)) {
        fields[match[1]] ??= match[2];
    }
    return fields;
}

function requiredField(key, fields, name) {
    if (fields[name] === undefined) {
        throw new Refusal(`${key}.md names no ${name} in its header.`);
    }
    return fields[name];
}

function boardOf(sections) {
    return sections.map(section => {
        const ranked = section.items.some(item => item.severity !== undefined);
        const header = ['Order', 'Item', 'Title', 'Size', ...(ranked ? ['Severity'] : []), 'Blocked by', 'Status', 'Issue'];
        const rows = section.items.map(item => [item.order, item.key, item.title, item.size, ...(ranked ? [item.severity] : []), item.blockedBy,
            item.status, item.issue]);
        return [`### ${section.heading}`, '', tableRow(header), tableRow(header.map(() => '---')), ...rows.map(tableRow)].join('\n');
    }).join('\n\n');
}

function nextOf(sections) {
    const items = sections.flatMap(section => section.items.map(item => ({ ...item, section: section.heading })));
    const statusOf = Object.fromEntries(items.map(item => [item.key, item.status]));
    const next = items.filter(item => item.status === 'open')
        .filter(item => (item.blockedBy.match(KEY) ?? []).every(blocker => statusOf[blocker] === 'delivered'));
    if (next.length === 0) {
        return 'No open item has all its blockers delivered.';
    }
    return next.map(item => `${item.key} ${item.title} (${item.section.split(':')[0]}, order ${item.order}) ${item.issue}`).join('\n');
}

function tableRow(cells) {
    return `| ${cells.join(' | ')} |`;
}

function cellsOf(row) {
    return row.split('|').slice(1, -1).map(cell => cell.trim());
}
