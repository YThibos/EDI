alter table task_cost add column worker_model text;
alter table task_cost add column worker_effort text check (worker_effort in ('low', 'medium', 'high', 'xhigh', 'max'));

update task_cost
set worker_model  = (select model from worker where worker.task_id = task_cost.task_id order by incarnation limit 1),
    worker_effort = (select effort from worker where worker.task_id = task_cost.task_id order by incarnation limit 1);
