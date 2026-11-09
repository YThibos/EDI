alter table pipeline_run add column checks_passed integer not null default 0 check (checks_passed in (0, 1));
