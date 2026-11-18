alter table delivery add column checks text not null default 'undecided' check (checks in ('passed', 'failed', 'undecided'));

alter table pipeline_run rename column active_step to step;
alter table pipeline_run add column stopped_by_the_pipeline_service integer not null default 0
    check (stopped_by_the_pipeline_service in (0, 1));
