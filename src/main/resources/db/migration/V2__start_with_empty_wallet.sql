delete from credential;

insert into wallet (id, user_hash)
values ('wallet-primary', 'local_user')
on conflict (id) do nothing;
