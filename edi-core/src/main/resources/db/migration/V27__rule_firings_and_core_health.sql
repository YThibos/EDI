create table rule_fired (
    number     integer not null primary key autoincrement,
    at         text    not null,
    rule       text    not null,
    outcome    text    not null check (outcome in ('refused', 'warned', 'allowed')),
    actor      text    not null,
    task_id    text,
    session_id text,
    reason     text    not null check (length(reason) <= 200)
) strict;

create index rule_fired_by_rule on rule_fired (rule, at);

create table health_sample (
    at      text    not null,
    measure text    not null,
    count   integer not null check (count >= 0),
    total   real    not null check (total >= 0),
    max     real    not null check (max >= 0)
) strict;

create index health_sample_by_time on health_sample (at);
