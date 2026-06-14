CREATE TABLE lojas (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  empresa_id UUID NOT NULL REFERENCES empresas(id),
  nome VARCHAR(200) NOT NULL,
  cnpj VARCHAR(18),
  descricao TEXT,
  cor VARCHAR(7) NOT NULL DEFAULT '#3B82F6',
  ativo BOOLEAN NOT NULL DEFAULT true,
  criado_em TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_lojas_empresa ON lojas(empresa_id);
