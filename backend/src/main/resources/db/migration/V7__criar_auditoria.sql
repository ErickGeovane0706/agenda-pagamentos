CREATE TABLE auditoria (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  empresa_id UUID NOT NULL REFERENCES empresas(id),
  usuario_id UUID REFERENCES usuarios(id),
  usuario_nome VARCHAR(200),
  acao VARCHAR(50) NOT NULL,
  entidade VARCHAR(50) NOT NULL,
  entidade_id UUID,
  detalhes TEXT,
  ip VARCHAR(45),
  criado_em TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_auditoria_empresa ON auditoria(empresa_id);
CREATE INDEX idx_auditoria_criado_em ON auditoria(criado_em);
