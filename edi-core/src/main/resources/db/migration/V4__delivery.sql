alter table task add column delivery text check (delivery in ('draft-review', 'local-branch', 'local-apply'));

create table delivery (
    task_id       text    not null primary key references task (id),
    provider      text    not null check (provider in ('gitlab', 'github')),
    host          text    not null,
    path          text    not null,
    number        integer not null check (number > 0),
    url           text    not null,
    draft         integer not null check (draft in (0, 1)),
    head_commit   text,
    state         text    not null check (state in ('open', 'merged', 'closed')),
    landed_commit text,
    recorded_at   text    not null,
    observed_at   text    not null,
    check ((state = 'merged') = (landed_commit is not null))
) strict;

create index delivery_by_state on delivery (state);

create table pipeline_run (
    run_id        text not null primary key,
    task_id       text not null references task (id),
    status        text not null,
    active_step   text,
    head_commit   text,
    merge_request text,
    seen_at       text not null,
    observed_at   text not null
) strict;

create index pipeline_run_by_task on pipeline_run (task_id, seen_at);

create table clone_refresh (
    task_id         text not null primary key references task (id),
    project         text not null,
    base            text not null,
    expected_commit text not null,
    requested_at    text not null,
    verified_at     text,
    clean_up_tried_at text,
    check (clean_up_tried_at is null or verified_at is not null)
) strict;

create index clone_refresh_by_project on clone_refresh (project, verified_at);

create table task_cost (
    task_id                     text    not null primary key references task (id),
    worker_input_tokens         integer not null,
    worker_cache_read_tokens    integer not null,
    worker_cache_write_tokens   integer not null,
    worker_output_tokens        integer not null,
    pipeline_input_tokens       integer,
    pipeline_cache_read_tokens  integer,
    pipeline_cache_write_tokens integer,
    pipeline_output_tokens      integer,
    counted_at                  text    not null,
    check ((pipeline_input_tokens is null) = (pipeline_output_tokens is null))
) strict;
