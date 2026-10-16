create table worker_with_supervision (
    id                  text    not null primary key,
    task_id             text    not null references task (id),
    incarnation         integer not null,
    session_id          text    not null,
    launch_kind         text    not null check (launch_kind in ('dispatch', 'resume', 'fresh')),
    boot_id             text,
    category            text    not null,
    model               text    not null,
    effort              text check (effort in ('low', 'medium', 'high')),
    token_hash          text    not null unique,
    tmux_socket         text    not null,
    server_pid          integer not null,
    server_started_at   text    not null,
    window_id           text    not null,
    pane_pid            integer not null,
    activity            text    not null check (activity in ('starting', 'busy', 'idle')),
    activity_at         text    not null,
    launched_at         text    not null,
    started_at          text,
    briefed_at          text,
    transcript_path     text,
    ended_at            text,
    stop_requested_at   text,
    launch_failed_at    text,
    retired_at          text,
    status_state        text check (status_state in ('working', 'needs-decision', 'blocked', 'paused', 'done', 'failed', 'resolved')),
    status_key          text,
    status_until        text,
    status_text         text,
    status_at           text,
    done_check          text check (done_check in ('checking', 'accepted', 'refused')),
    limit_name          text,
    limit_resets_at     text,
    limit_parked_at     text,
    unique (task_id, incarnation),
    check ((status_state is null) = (status_at is null)),
    check ((limit_name is null) = (limit_parked_at is null))
) strict;

insert into worker_with_supervision (
        id, task_id, incarnation, session_id, launch_kind, boot_id, category, model, effort, token_hash, tmux_socket, server_pid,
        server_started_at, window_id, pane_pid, activity, activity_at, launched_at, started_at, briefed_at, launch_failed_at, retired_at,
        status_state, status_key, status_until, status_text, status_at, done_check)
    select id, task_id, incarnation, session_id, 'dispatch', null, category, model, effort, token_hash, tmux_socket, server_pid,
           server_started_at, window_id, pane_pid, activity, coalesce(briefed_at, started_at, launched_at), launched_at, started_at, briefed_at,
           launch_failed_at, retired_at, status_state, status_key, status_until, status_text, status_at, done_check
    from worker;

drop table worker;

alter table worker_with_supervision rename to worker;

create index worker_by_task on worker (task_id, incarnation);

create table worker_notice (
    worker_id  text not null references worker (id),
    kind       text not null check (kind in ('died', 'stale', 'busy-too-long', 'wait-ended', 'limit-resumed', 'limit-held-back')),
    since      text not null,
    noticed_at text not null,
    primary key (worker_id, kind, since)
) strict;

create table recovery_cost (
    worker_id          text    not null primary key references worker (id),
    input_tokens       integer not null,
    cache_read_tokens  integer not null,
    cache_write_tokens integer not null,
    output_tokens      integer not null,
    counted_at         text    not null
) strict;

create table outage_entry (
    number    integer not null primary key,
    task_id   text    not null unique references task (id),
    worker_id text    not null references worker (id),
    cause     text    not null check (cause in ('machine-restarted', 'server-gone', 'server-changed', 'window-closed')),
    listed_at text    not null
) strict;

create table primary_session (
    session_id        text    not null primary key,
    started_at        text    not null,
    claude_pid        integer,
    claude_started_at text,
    claimed_at        text,
    turn_started_at   text,
    turn_event_mark   integer,
    reported_at       text,
    stops_blocked     integer,
    typed_at          text,
    notification      text,
    notified_at       text,
    limit_name        text,
    limit_resets_at   text,
    limit_reached_at  text,
    ended_at          text,
    check ((turn_started_at is null) = (turn_event_mark is null)),
    check ((turn_started_at is null) = (stops_blocked is null)),
    check ((limit_name is null) = (limit_reached_at is null))
) strict;

create table typed_prompt (
    session_id text not null references primary_session (session_id),
    typed_at   text not null,
    digest     text not null
) strict;

create index typed_prompt_by_session on typed_prompt (session_id);

create table wake (
    number          integer not null primary key autoincrement,
    event_sequence  integer not null unique references event (sequence),
    kind            text    not null,
    task_id         text    not null references task (id),
    worker_id       text,
    detail          text    not null,
    moment          text,
    raised_at       text    not null,
    delivered_to    text,
    delivered_at    text,
    acknowledged_at text,
    check ((delivered_to is null) = (delivered_at is null))
) strict;

create index wake_unacknowledged on wake (acknowledged_at, number);

create table hold_notice (
    task_id    text not null references task (id),
    set_at     text not null,
    noticed_at text not null,
    primary key (task_id, set_at)
) strict;
