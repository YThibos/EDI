create table clone_refresh_tried_again (
    task_id         text not null primary key references task (id),
    project         text not null,
    base            text not null,
    expected_commit text not null,
    requested_at    text not null,
    verified_at     text
) strict;

insert into clone_refresh_tried_again (task_id, project, base, expected_commit, requested_at, verified_at)
select task_id, project, base, expected_commit, requested_at, verified_at from clone_refresh;

drop table clone_refresh;

alter table clone_refresh_tried_again rename to clone_refresh;

create index clone_refresh_by_project on clone_refresh (project, verified_at);
