create table workflow_status (
    project    text not null check (length(project) > 0),
    issue_type text not null,
    status     text not null check (length(status) > 0),
    primary key (project, issue_type, status)
) strict;

create table workflow_transition (
    project     text not null,
    issue_type  text not null,
    from_status text not null,
    to_status   text not null check (length(to_status) > 0),
    primary key (project, issue_type, from_status, to_status),
    foreign key (project, issue_type, from_status) references workflow_status (project, issue_type, status) on delete cascade
) strict;
