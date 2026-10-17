create table console_sign_in (
    digest     text not null primary key,
    issued_at  text not null,
    expires_at text not null,
    used_at    text
) strict;

create table console_session (
    digest     text not null primary key,
    opened_at  text not null,
    expires_at text not null
) strict;

create table board (
    id         integer not null primary key autoincrement,
    task_id    text    not null references task (id),
    file       text    not null,
    rounds     integer not null check (rounds >= 0),
    armed_at   text    not null,
    ended_at   text,
    retired_at text
) strict;

create unique index board_listening_once on board (file) where ended_at is null and retired_at is null;

create table board_decision (
    board_id        integer not null references board (id),
    position        integer not null,
    decision_number integer not null references decision (number),
    primary key (board_id, position),
    unique (board_id, decision_number)
) strict;

create table board_answer (
    board_id        integer not null references board (id),
    round           integer not null check (round > 0),
    decision_number integer not null references decision (number),
    verbatim        text    not null,
    option_key      text,
    received_at     text    not null,
    settled_at      text,
    refused_because text,
    primary key (board_id, round, decision_number),
    check (refused_because is null or settled_at is not null)
) strict;
