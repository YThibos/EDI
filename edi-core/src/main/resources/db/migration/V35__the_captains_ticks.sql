create table call_tick (
    call      text not null primary key,
    request   text,
    ticked_at text not null
) strict;

insert into call_tick (call, ticked_at)
select 'D' || number, answered_at from decision where answered_at is not null;

insert into call_tick (call, ticked_at)
select 'board:' || id, coalesce(ended_at, retired_at) from board where ended_at is not null or retired_at is not null;

insert into call_tick (call, request, ticked_at)
select 'review:' || task_id, url, observed_at from delivery where state in ('merged', 'closed');
