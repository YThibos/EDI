drop table typed_passage;

create table captain_message (
    session_id text    not null references primary_session (session_id),
    number     integer not null,
    kind       text    not null check (kind in ('typed', 'dialog-answer')),
    text       text,
    question   text,
    chosen     text,
    notes      text,
    sent_at    text    not null,
    primary key (session_id, number),
    check ((kind = 'typed') = (text is not null)),
    check ((kind = 'dialog-answer') = (question is not null and chosen is not null))
) strict;
