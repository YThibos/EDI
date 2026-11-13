create table turn_item (
    session_id text    not null references primary_session (session_id),
    position   integer not null check (position >= 0),
    part       text    not null check (part in ('done', 'next', 'task-wording')),
    task_id    text,
    text       text,
    state      text,
    projects   text,
    primary key (session_id, position),
    check ((part = 'task-wording') = (task_id is not null)),
    check (part = 'task-wording' or (text is not null and projects is not null and state is null))
) strict;
