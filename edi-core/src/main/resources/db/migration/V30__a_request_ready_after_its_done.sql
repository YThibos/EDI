alter table delivery add column checks_passed integer not null default 0 check (checks_passed in (0, 1));

alter table pipeline_run rename column active_step to step;
