alter table task add column delivery_mode text check (delivery_mode in ('draft-review', 'local-branch', 'local-apply', 'report'));

update task set delivery_mode = delivery;

alter table task drop column delivery;

alter table task rename column delivery_mode to delivery;
