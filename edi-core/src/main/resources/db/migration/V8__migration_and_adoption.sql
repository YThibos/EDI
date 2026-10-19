create table adoption (
    task_id    text not null primary key references task (id),
    branch     text not null,
    session_id text,
    adopted_at text not null
) strict;
