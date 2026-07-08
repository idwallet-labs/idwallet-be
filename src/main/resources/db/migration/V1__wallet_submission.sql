create table wallet (
  id uuid primary key,
  user_hash varchar(128) not null,
  created_at timestamptz not null default now()
);

create table credential (
  id uuid primary key,
  wallet_id uuid not null references wallet(id),
  type varchar(80) not null,
  issuer_name varchar(120) not null,
  payload_hash varchar(128) not null,
  status varchar(24) not null,
  expires_at date not null
);

create table submission_request (
  id uuid primary key,
  requested_types jsonb not null,
  status varchar(24) not null,
  expires_at timestamptz not null
);

create table submission_response (
  id uuid primary key,
  request_id uuid not null references submission_request(id),
  credential_id uuid not null references credential(id),
  result varchar(24) not null,
  created_at timestamptz not null default now()
);
