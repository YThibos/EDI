alter table board add column link text;

alter table board add column open_questions integer not null default 0 check (open_questions >= 0);

delete from task_dependency
where blocked_by in (select id from task where state in ('done', 'dropped'));
