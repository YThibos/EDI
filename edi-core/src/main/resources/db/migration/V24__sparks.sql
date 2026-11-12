create table spark (
    number        integer not null primary key,
    text          text    not null,
    author        text    not null check (author in ('captain', 'primary')),
    session_id    text,
    focus_task    text references task (id),
    focus_project text,
    caught_at     text    not null,
    outcome       text check (outcome in ('backlogged', 'folded', 'dropped')),
    item_key      text,
    reason        text,
    sorted_at     text,
    check (focus_task is null or focus_project is null),
    check ((outcome is null) = (sorted_at is null)),
    check ((coalesce(outcome, '') in ('backlogged', 'folded')) = (item_key is not null)),
    check ((coalesce(outcome, '') = 'dropped') = (reason is not null))
) strict;

create index open_spark on spark (number) where outcome is null;
