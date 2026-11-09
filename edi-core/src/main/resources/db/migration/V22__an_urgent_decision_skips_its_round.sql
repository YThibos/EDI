alter table decision add column urgent integer not null default 0 check (urgent in (0, 1));
