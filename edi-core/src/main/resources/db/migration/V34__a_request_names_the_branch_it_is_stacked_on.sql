alter table delivery add column stacked_on text check (stacked_on is null or length(stacked_on) > 0);
