alter table task add column size text check (size in ('XS', 'S', 'M', 'L'));
