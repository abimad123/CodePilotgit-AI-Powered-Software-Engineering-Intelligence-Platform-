CREATE TABLE users (
  id UUID PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  email VARCHAR(320) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','DISABLED')),
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT users_email_normalized CHECK (email = lower(email))
);

CREATE TABLE refresh_tokens (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  token_hash VARCHAR(64) NOT NULL UNIQUE CHECK (length(token_hash) = 64),
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL,
  replaced_by_token_id UUID REFERENCES refresh_tokens(id) ON DELETE RESTRICT
);
CREATE INDEX refresh_tokens_user_idx ON refresh_tokens(user_id);

CREATE TABLE projects (
  id UUID PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  description VARCHAR(1000),
  slug VARCHAR(140) NOT NULL UNIQUE,
  owner_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  deleted_at TIMESTAMPTZ
);
CREATE INDEX projects_owner_idx ON projects(owner_id);

CREATE TABLE project_memberships (
  id UUID PRIMARY KEY,
  project_id UUID NOT NULL REFERENCES projects(id) ON DELETE RESTRICT,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  role VARCHAR(20) NOT NULL CHECK (role IN ('OWNER','ADMIN','MEMBER')),
  joined_at TIMESTAMPTZ NOT NULL,
  UNIQUE (project_id, user_id)
);
CREATE INDEX project_memberships_user_idx ON project_memberships(user_id);

CREATE TABLE github_installations (
  id UUID PRIMARY KEY,
  installation_id BIGINT NOT NULL UNIQUE,
  account_login VARCHAR(255) NOT NULL,
  account_type VARCHAR(50) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE project_github_installations (
  project_id UUID NOT NULL REFERENCES projects(id) ON DELETE RESTRICT,
  github_installation_id UUID NOT NULL REFERENCES github_installations(id) ON DELETE RESTRICT,
  PRIMARY KEY (project_id, github_installation_id)
);
CREATE TABLE github_connection_states (
  id UUID PRIMARY KEY,
  state_hash VARCHAR(64) NOT NULL UNIQUE CHECK (length(state_hash) = 64),
  project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  intended_installation_id BIGINT NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  consumed_at TIMESTAMPTZ
);
CREATE INDEX github_connection_states_expiry_idx ON github_connection_states(expires_at);

CREATE TABLE project_repositories (
  id UUID PRIMARY KEY,
  project_id UUID NOT NULL REFERENCES projects(id) ON DELETE RESTRICT,
  github_installation_id UUID NOT NULL REFERENCES github_installations(id) ON DELETE RESTRICT,
  repository_id BIGINT NOT NULL,
  repository_full_name VARCHAR(512) NOT NULL,
  description VARCHAR(2000),
  default_branch VARCHAR(255) NOT NULL,
  repository_private BOOLEAN NOT NULL,
  html_url VARCHAR(2000) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  UNIQUE (project_id, repository_id),
  FOREIGN KEY (project_id, github_installation_id)
    REFERENCES project_github_installations(project_id, github_installation_id)
);
CREATE INDEX project_repositories_project_idx ON project_repositories(project_id);
