create table call_tick (
    call      text not null primary key,
    subject   text,
    ticked_at text not null
) strict;

insert into call_tick (call, ticked_at)
select 'D' || number, answered_at from decision where answered_at is not null;

insert into call_tick (call, subject, ticked_at)
select 'board:' || id, cast(rounds as text), coalesce(ended_at, retired_at, armed_at) from board
where not (ended_at is null and retired_at is null
           and (open_questions > 0
                or exists (select 1 from board_decision join decision on decision.number = board_decision.decision_number
                           where board_decision.board_id = board.id and decision.answered_at is null)));

insert into call_tick (call, subject, ticked_at)
select 'review:' || task_id, url, observed_at from delivery where state in ('merged', 'closed');
