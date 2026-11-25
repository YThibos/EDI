create table task_hold (
    task_id   text    not null references task (id),
    kind      text    not null check (kind in ('decision', 'deferral', 'approval', 'outage')),
    decision  integer references decision (number) deferrable initially deferred,
    outage    integer check (outage is null or outage >= 1),
    why       text check (why is null or length(why) > 0),
    until     text,
    set_at    text    not null,
    check ((kind = 'decision') = (decision is not null)),
    check ((kind = 'outage') = (outage is not null)),
    check ((kind = 'decision') = (why is null)),
    check (kind in ('deferral', 'approval') or until is null)
) strict;

create unique index task_hold_of_each_kind_once on task_hold (task_id, kind) where kind <> 'decision';

create unique index task_hold_of_each_decision_once on task_hold (decision) where decision is not null;

insert into task_hold (task_id, kind, decision, set_at)
select decision.task_id, 'decision', decision.number, decision.raised_at
from decision
join task on task.id = decision.task_id
where decision.answered_at is null
  and task.stage not in ('retired', 'dropped');

insert into task_hold (task_id, kind, outage, why, set_at)
select captain_hold.task_id, 'outage', outage_entry.number, substr(captain_hold.reason, 1, instr(captain_hold.reason, '; number ') - 1), captain_hold.set_at
from captain_hold
join outage_entry on outage_entry.task_id = captain_hold.task_id
where captain_hold.kind = 'approval'
  and captain_hold.reason like 'lost when %; number ' || outage_entry.number || ' on the outage hold list waits for the captain to resume it or start it fresh';

insert into task_hold (task_id, kind, why, until, set_at)
select captain_hold.task_id, captain_hold.kind, captain_hold.reason, captain_hold.until, captain_hold.set_at
from captain_hold
where captain_hold.kind in ('deferral', 'approval')
  and not exists (select 1 from task_hold where task_hold.task_id = captain_hold.task_id and task_hold.kind = 'outage');

drop table captain_hold;
