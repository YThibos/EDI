create table meta (
    name  text    not null primary key,
    value integer not null
) strict;

insert into meta (name, value) values ('decision_seq', 0);

create table task (
    id         text not null primary key,
    kind       text not null check (kind in ('ship', 'scout', 'captain')),
    title      text not null,
    state      text not null check (state in ('queued', 'in-flight', 'done')),
    work_key   text,
    base       text,
    notes      text not null,
    created_at text not null,
    closed_at  text,
    check ((state = 'done') = (closed_at is not null))
) strict;

create index task_by_state on task (state);

create table task_project (
    task_id  text    not null references task (id),
    position integer not null,
    project  text    not null,
    primary key (task_id, position),
    unique (task_id, project)
) strict;

create table task_dependency (
    task_id    text not null references task (id),
    blocked_by text not null references task (id),
    primary key (task_id, blocked_by)
) strict;

create table captain_hold (
    task_id text not null primary key references task (id),
    kind    text not null check (kind in ('decision', 'deferral', 'approval')),
    reason  text not null,
    until   text,
    set_at  text not null
) strict;

create table decision (
    number                integer not null primary key,
    task_id               text    not null unique references task (id),
    title                 text    not null,
    context               text    not null,
    recommended           text    not null,
    recommended_because   text    not null,
    raised_at             text    not null,
    answer_verbatim       text,
    answer_option         text,
    answer_interpretation text,
    answered_at           text,
    answer_channel        text check (answer_channel in ('chat', 'console', 'board')),
    check ((answer_verbatim is null) = (answered_at is null))
) strict;

create table decision_option (
    decision_number integer not null references decision (number),
    position        integer not null,
    option_key      text    not null,
    option_text     text    not null,
    primary key (decision_number, position),
    unique (decision_number, option_key)
) strict;

create table event (
    sequence integer not null primary key autoincrement,
    at       text    not null,
    actor    text    not null,
    type     text    not null,
    task_id  text references task (id),
    payload  text    not null
) strict;

create index event_by_task on event (task_id, sequence);
