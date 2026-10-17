create table work_key_sequence (
    prefix      text    not null primary key,
    last_number integer not null check (last_number > 0)
) strict;

create table ticket_proposal (
    task_id         text not null primary key references task (id),
    project_key     text not null,
    issue_type      text not null,
    summary         text not null,
    description     text not null,
    epic            text,
    labels          text not null,
    priority        text not null,
    proposed_at     text not null,
    create_tried_at text,
    created_key     text,
    filled_at       text,
    settled_key     text,
    settled_at      text,
    refusal         text,
    check ((settled_key is null) = (settled_at is null)),
    check (filled_at is null or created_key is not null)
) strict;

create table ticket_candidate (
    task_id  text    not null references ticket_proposal (task_id),
    position integer not null,
    work_key text    not null,
    summary  text    not null,
    status   text    not null,
    primary key (task_id, position)
) strict;

create table event_cursor (
    follower text    not null primary key,
    sequence integer not null check (sequence >= 0)
) strict;

insert into event_cursor (follower, sequence) select 'fleet-ledger', coalesce(max(sequence), 0) from event;
insert into event_cursor (follower, sequence) select 'tickets', coalesce(max(sequence), 0) from event;
insert into event_cursor (follower, sequence) select 'research-first-pilot', coalesce(max(sequence), 0) from event;

create table unplanned_ship (
    ship_id       text not null primary key references task (id),
    category      text not null,
    research_id   text unique references task (id),
    dispatched_at text not null,
    check (research_id is null or research_id != ship_id)
) strict;

create table task_review (
    task_id    text not null primary key references task (id),
    rounds     integer check (rounds >= 0),
    findings   integer check (findings >= 0),
    counted_at text not null
) strict;
