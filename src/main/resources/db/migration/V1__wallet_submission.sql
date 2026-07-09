create table wallet (
  id varchar(64) primary key,
  user_hash varchar(128) not null,
  created_at timestamptz not null default now()
);

create table credential (
  id varchar(64) primary key,
  wallet_id varchar(64) not null references wallet(id),
  type varchar(80) not null,
  issuer_name varchar(120) not null,
  payload_hash varchar(128) not null,
  status varchar(24) not null,
  expires_at date not null
);

create table submission_request (
  id varchar(64) primary key,
  requested_types jsonb not null,
  status varchar(24) not null,
  expires_at timestamptz not null
);

create table submission_response (
  id varchar(96) primary key,
  request_id varchar(64) not null references submission_request(id),
  credential_id varchar(64) not null references credential(id),
  result varchar(24) not null,
  created_at timestamptz not null default now()
);

create table audit_event (
  id varchar(96) primary key,
  type varchar(64) not null,
  target_id varchar(96) not null,
  detail varchar(255) not null,
  created_at timestamptz not null default now()
);

insert into wallet (id, user_hash)
values ('wallet-demo-1', 'user_hash_demo_1001');

insert into credential (id, wallet_id, type, issuer_name, payload_hash, status, expires_at)
values
  ('wallet-vc-1', 'wallet-demo-1', '교육 수료 증명', 'IDWallet Demo Issuer', 'hash_education_1001', 'ACTIVE', date '2027-12-31'),
  ('wallet-vc-2', 'wallet-demo-1', '재직 증명', 'IDWallet Demo Issuer', 'hash_employment_1002', 'ACTIVE', date '2026-10-31');
