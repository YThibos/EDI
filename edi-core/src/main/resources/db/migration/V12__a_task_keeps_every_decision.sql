pragma defer_foreign_keys = on;

create temporary table decision_kept as select * from decision;

drop table decision;

create table decision (
    number                integer not null primary key,
    task_id               text    not null references task (id),
    title                 text    not null,
    context               text    not null,
    recommended           text    not null,
    recommended_because   text    not null,
    raised_at             text    not null,
    answer_verbatim       text,
    answer_option         text,
    answer_interpretation text,
    answered_at           text,
    answer_channel        text check (answer_channel in ('chat', 'console', 'board')),
    check ((answer_verbatim is null) = (answered_at is null))
) strict;

insert into decision
    select number, task_id, title, context, recommended, recommended_because, raised_at,
           answer_verbatim, answer_option, answer_interpretation, answered_at, answer_channel
    from decision_kept;

drop table decision_kept;

create index decision_by_task on decision (task_id, number);
