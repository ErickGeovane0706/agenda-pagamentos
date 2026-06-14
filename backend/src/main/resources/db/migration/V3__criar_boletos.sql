CREATE TABLE boletos (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  empresa_id UUID NOT NULL REFERENCES empresas(id),
  loja_id UUID NOT NULL REFERENCES lojas(id),
  fornecedor VARCHAR(300) NOT NULL,
  valor NUMERIC(15,2) NOT NULL,
  vencimento DATE NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE' CHECK (status IN ('PENDENTE','PAGO','VENCIDO','CANCELADO')),
  codigo_barras TEXT,
  url_arquivo TEXT,
  nome_arquivo VARCHAR(300),
  observacoes TEXT,
  pago_em TIMESTAMP,
  criado_em TIMESTAMP NOT NULL DEFAULT NOW(),
  atualizado_em TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_boletos_empresa ON boletos(empresa_id);
CREATE INDEX idx_boletos_loja ON boletos(loja_id);
CREATE INDEX idx_boletos_vencimento ON boletos(vencimento);
CREATE INDEX idx_boletos_status ON boletos(status);
