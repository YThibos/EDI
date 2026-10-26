pragma defer_foreign_keys = on;

create temporary table dispatch_kept as select * from dispatch;
create temporary table worker_kept as select * from worker;

drop table dispatch;
drop table worker;

create table dispatch (
    task_id         text not null primary key references task (id),
    category        text not null,
    branch          text not null,
    spec            text not null,
    expected_change text,
    override_model  text,
    override_effort text check (override_effort in ('low', 'medium', 'high', 'xhigh', 'max')),
    override_reason text,
    requested_at    text not null,
    deferred_at     text,
    check ((override_model is null) = (override_reason is null))
) strict;

create table worker (
    id                 text    not null primary key,
    task_id            text    not null references task (id),
    incarnation        integer not null,
    session_id         text    not null,
    launch_kind        text    not null check (launch_kind in ('dispatch', 'resume', 'fresh')),
    boot_id            text,
    category           text    not null,
    model              text    not null,
    effort             text check (effort in ('low', 'medium', 'high', 'xhigh', 'max')),
    token_hash         text    not null unique,
    tmux_socket        text    not null,
    server_pid         integer not null,
    server_started_at  text    not null,
    window_id          text    not null,
    pane_pid           integer not null,
    activity           text    not null check (activity in ('starting', 'busy', 'idle')),
    activity_at        text    not null,
    launched_at        text    not null,
    started_at         text,
    briefed_at         text,
    transcript_path    text,
    ended_at           text,
    stop_requested_at  text,
    launch_failed_at   text,
    retired_at         text,
    status_state       text check (status_state in ('working', 'needs-decision', 'blocked', 'paused', 'done', 'failed', 'resolved')),
    status_key         text,
    status_until       text,
    status_text        text,
    status_at          text,
    done_check         text check (done_check in ('checking', 'accepted', 'refused')),
    limit_name         text,
    limit_resets_at    text,
    limit_parked_at    text,
    context_full_since text,
    unique (task_id, incarnation),
    check ((status_state is null) = (status_at is null)),
    check ((limit_name is null) = (limit_parked_at is null))
) strict;

insert into dispatch select * from dispatch_kept;
insert into worker select * from worker_kept;

drop table dispatch_kept;
drop table worker_kept;

create index worker_by_task on worker (task_id, incarnation);

alter table captain_word add column act text check (act in ('effort-above-high'));
