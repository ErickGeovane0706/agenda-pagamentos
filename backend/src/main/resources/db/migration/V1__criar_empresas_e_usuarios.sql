CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE empresas (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  nome VARCHAR(200) NOT NULL,
  ativo BOOLEAN NOT NULL DEFAULT true,
  criado_em TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE usuarios (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  empresa_id UUID NOT NULL REFERENCES empresas(id),
  nome VARCHAR(200) NOT NULL,
  email VARCHAR(200) NOT NULL UNIQUE,
  senha_hash VARCHAR(300) NOT NULL,
  perfil VARCHAR(20) NOT NULL CHECK (perfil IN ('ADMIN','OPERADOR','VIEWER')),
  telefone_whatsapp VARCHAR(20),
  aceite_whatsapp BOOLEAN NOT NULL DEFAULT false,
  aceite_whatsapp_em TIMESTAMP,
  ativo BOOLEAN NOT NULL DEFAULT true,
  criado_em TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_usuarios_empresa ON usuarios(empresa_id);
CREATE INDEX idx_usuarios_email ON usuarios(email);
