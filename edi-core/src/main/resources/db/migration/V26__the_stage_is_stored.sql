pragma defer_foreign_keys = on;

create temporary table task_kept as select * from task;

drop table task;

create table task (
    id         text not null primary key,
    kind       text not null check (kind in ('ship', 'scout', 'captain')),
    title      text not null,
    stage      text not null check (stage in ('queued', 'working', 'delivered', 'changing', 'ready', 'merged', 'landed', 'retired', 'dropped')),
    work_key   text,
    base       text,
    notes      text not null,
    created_at text not null,
    closed_at  text,
    delivery   text check (delivery in ('draft-review', 'local-branch', 'local-apply', 'report')),
    check ((stage in ('retired', 'dropped')) = (closed_at is not null))
) strict;

create index task_by_stage on task (stage);

insert into task (id, kind, title, stage, work_key, base, notes, created_at, closed_at, delivery)
select id,
       kind,
       title,
       case
           when state = 'queued' then 'queued'
           when state = 'done' then 'retired'
           when state = 'dropped' then 'dropped'
           when exists (select 1 from delivery where delivery.task_id = task_kept.id and delivery.state = 'merged') then 'merged'
           when (select done_check from worker where worker.task_id = task_kept.id order by incarnation desc limit 1) = 'accepted' then 'delivered'
           when exists (
               select 1 from event left join worker on worker.id = json_extract(event.payload, '$.worker')
               where event.task_id = task_kept.id
                 and (event.type = 'MechanicalChangeAccepted'
                      or event.type = 'StatusReported' and json_extract(event.payload, '$.state') = 'done' and worker.category <> 'mechanical'))
               then 'changing'
           else 'working'
       end,
       work_key,
       base,
       notes,
       created_at,
       closed_at,
       delivery
from task_kept
order by rowid;

drop table task_kept;

with heads_after_the_merge as (
    select delivery.task_id, run.head_commit, min(run.seen_at) as first_seen
    from delivery
    join pipeline_run run on run.task_id = delivery.task_id
    where delivery.state = 'merged'
      and run.head_commit is not null
      and run.head_commit <> delivery.head_commit
      and run.seen_at > (select max(merged_run.seen_at) from pipeline_run merged_run
                         where merged_run.task_id = delivery.task_id and merged_run.head_commit = delivery.head_commit)
    group by delivery.task_id, run.head_commit
),
not_landed as (
    select task_id,
           count(*) as commits,
           group_concat(substr(head_commit, 1, 12), ', ' order by first_seen) as short_ids,
           json_group_array(json_object('id', head_commit, 'subject', '') order by first_seen) as listed
    from heads_after_the_merge
    group by task_id
),
derived as (
    select task.rowid as position,
           task.id,
           task.stage,
           case task.stage
               when 'working' then 'dispatched'
               when 'delivered' then 'its worker reported done'
               when 'changing' then 'its worker works again after its done'
               else coalesce('merged at ' || delivery.head_commit, 'merged')
                    || case
                           when not_landed.commits is null then ''
                           when not_landed.commits = 1 then '; 1 commit not landed: ' || not_landed.short_ids
                           else '; ' || not_landed.commits || ' commits not landed: ' || not_landed.short_ids
                       end
           end as reason,
           coalesce(not_landed.listed, '[]') as not_landed,
           coalesce((select event.at from event where event.task_id = task.id order by event.sequence desc limit 1), task.created_at) as at
    from task
    left join delivery on delivery.task_id = task.id
    left join not_landed on not_landed.task_id = task.id
    where task.stage in ('working', 'delivered', 'changing', 'merged')
)
insert into event (at, actor, type, task_id, payload)
select at, 'core', 'StageDerived', id, json_object('stage', stage, 'reason', reason, 'notLanded', json(not_landed))
from derived
order by position;
