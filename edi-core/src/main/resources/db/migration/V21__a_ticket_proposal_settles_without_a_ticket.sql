pragma defer_foreign_keys = on;

create temporary table ticket_proposal_kept as select * from ticket_proposal;

drop table ticket_proposal;

create table ticket_proposal (
    task_id         text not null primary key references task (id),
    project_key     text not null,
    issue_type      text not null,
    summary         text not null,
    description     text not null,
    epic            text,
    labels          text not null,
    priority        text not null,
    proposed_at     text not null,
    create_tried_at text,
    created_key     text,
    filled_at       text,
    settled_key     text,
    settled_at      text,
    refusal         text,
    check (settled_key is null or settled_at is not null),
    check (filled_at is null or created_key is not null)
) strict;

insert into ticket_proposal
    select task_id, project_key, issue_type, summary, description, epic, labels, priority, proposed_at,
           create_tried_at, created_key, filled_at, settled_key, settled_at, refusal
    from ticket_proposal_kept;

drop table ticket_proposal_kept;
