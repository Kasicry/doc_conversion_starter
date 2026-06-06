create table stored_files (
    id uuid primary key,
    original_name varchar(255) not null,
    stored_name varchar(255) not null,
    storage_path text not null,
    content_type varchar(120) not null,
    size_bytes bigint not null,
    expires_at timestamptz not null,
    deleted boolean not null default false,
    created_at timestamptz not null
);

create table conversion_jobs (
    id uuid primary key,
    original_file_id uuid not null references stored_files(id),
    result_file_id uuid references stored_files(id),
    target_format varchar(20) not null,
    status varchar(20) not null,
    error_message text,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    completed_at timestamptz
);

create index idx_conversion_jobs_status on conversion_jobs(status);
create index idx_stored_files_expires_at on stored_files(expires_at);
