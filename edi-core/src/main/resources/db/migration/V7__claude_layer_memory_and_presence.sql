drop table typed_prompt;

create table typed_passage (
    session_id text not null references primary_session (session_id),
    typed_at   text not null,
    digest     text not null
) strict;

create index typed_passage_by_session on typed_passage (session_id, digest);

create table captain_word (
    number     integer not null primary key autoincrement,
    session_id text    not null references primary_session (session_id),
    task_id    text references task (id),
    verbatim   text    not null,
    said_at    text    not null
) strict;

create index captain_word_by_session on captain_word (session_id);

create table presence (
    id         integer not null primary key check (id = 1),
    mode       text    not null,
    since      text    not null,
    cause      text    not null,
    mandate    text,
    worker_cap integer,
    demo_until text,
    check ((mode = 'away') = (mandate is not null and worker_cap is not null))
) strict;

create table memory_check (
    checked_at text    not null primary key,
    tokens     integer not null,
    budget     integer not null
) strict;

alter table decision_option add column authorises text check (authorises in ('promote', 'drop', 'discard-work'));

create table unbriefed_absence (
    id         integer not null primary key check (id = 1),
    mode       text    not null check (mode in ('quiet', 'away')),
    since      text    not null,
    cause      text    not null,
    mandate    text,
    worker_cap integer,
    demo_until text
) strict;
