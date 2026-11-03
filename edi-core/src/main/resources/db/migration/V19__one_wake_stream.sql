create table wake_stream (
    id         text    not null primary key,
    session_id text    not null,
    pid        integer not null check (pid > 0),
    start_tick integer,
    follows    integer not null check (follows in (0, 1)),
    started_at text    not null,
    ended_at   text
) strict;

create index wake_stream_open on wake_stream (ended_at);

alter table wake add column offered_to text references wake_stream (id);

create index wake_by_task on wake (task_id, number);

alter table primary_session drop column turn_ended_waiting;

alter table primary_session drop column turn_ended_at;
