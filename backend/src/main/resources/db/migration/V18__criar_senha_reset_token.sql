CREATE TABLE senha_reset_token (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  usuario_id UUID NOT NULL REFERENCES usuarios(id),
  token_hash VARCHAR(64) NOT NULL UNIQUE,
  expira_em TIMESTAMP NOT NULL,
  usado_em TIMESTAMP,
  criado_em TIMESTAMP NOT NULL DEFAULT NOW()
);
