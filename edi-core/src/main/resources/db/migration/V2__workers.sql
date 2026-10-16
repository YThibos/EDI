create table dispatch (
    task_id         text not null primary key references task (id),
    category        text not null,
    branch          text not null,
    spec            text not null,
    expected_change text,
    override_model  text,
    override_effort text check (override_effort in ('low', 'medium', 'high')),
    override_reason text,
    requested_at    text not null,
    deferred_at     text,
    check ((override_model is null) = (override_reason is null))
) strict;

create table dispatch_file (
    task_id  text    not null references dispatch (task_id),
    position integer not null,
    path     text    not null,
    primary key (task_id, position),
    unique (task_id, path)
) strict;

create table worker (
    id                text    not null primary key,
    task_id           text    not null references task (id),
    incarnation       integer not null,
    session_id        text    not null unique,
    category          text    not null,
    model             text    not null,
    effort            text check (effort in ('low', 'medium', 'high')),
    token_hash        text    not null unique,
    tmux_socket       text    not null,
    server_pid        integer not null,
    server_started_at text    not null,
    window_id         text    not null,
    pane_pid          integer not null,
    activity          text    not null check (activity in ('starting', 'busy', 'idle')),
    launched_at       text    not null,
    started_at        text,
    briefed_at        text,
    launch_failed_at  text,
    retired_at        text,
    status_state      text check (status_state in ('working', 'needs-decision', 'blocked', 'paused', 'done', 'failed', 'resolved')),
    status_key        text,
    status_until      text,
    status_text       text,
    status_at         text,
    done_check        text check (done_check in ('checking', 'accepted', 'refused')),
    unique (task_id, incarnation),
    check ((status_state is null) = (status_at is null))
) strict;

create index worker_by_task on worker (task_id, incarnation);

create table inbox_message (
    task_id                text    not null references task (id),
    number                 integer not null,
    body                   text    not null,
    resolves               text,
    created_at             text    not null,
    received_at            text,
    acknowledged_at        text,
    reported_unreceived_at text,
    primary key (task_id, number)
) strict;
