alter table plan add column go_session text;
alter table plan add column go_message integer check (go_message >= 1);
