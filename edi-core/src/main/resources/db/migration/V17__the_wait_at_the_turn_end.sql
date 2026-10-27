alter table primary_session add column turn_ended_at text;
alter table primary_session add column turn_ended_waiting integer not null default 0 check (turn_ended_waiting in (0, 1));
