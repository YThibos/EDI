create table plan (
    work_key text    not null primary key,
    title    text    not null,
    project  text    not null,
    report   text    not null,
    go       text    not null,
    capacity integer not null check (capacity >= 1),
    effort   text    check (effort in ('low', 'medium', 'high', 'xhigh', 'max')),
    added_at text    not null
) strict;

create table plan_slice (
    plan_key       text    not null references plan (work_key),
    number         integer not null check (number >= 1),
    task_id        text    not null unique references task (id),
    category       text    not null,
    waits_after    integer,
    waits_until    text    check (waits_until in ('merged', 'landed')),
    primary key (plan_key, number),
    foreign key (plan_key, waits_after) references plan_slice (plan_key, number),
    check ((waits_after is null) = (waits_until is null)),
    check (waits_after < number)
) strict;

update rule_fired set rule = 'dispatch.waits-met' where rule = 'dispatch.blockers-done';
