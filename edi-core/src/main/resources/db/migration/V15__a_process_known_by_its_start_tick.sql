alter table primary_session drop column claude_started_at;
alter table primary_session add column claude_start_tick integer;
