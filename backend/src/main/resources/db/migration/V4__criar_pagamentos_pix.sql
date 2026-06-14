CREATE TABLE pagamentos_pix (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  empresa_id UUID NOT NULL REFERENCES empresas(id),
  loja_id UUID NOT NULL REFERENCES lojas(id),
  fornecedor VARCHAR(300) NOT NULL,
  valor NUMERIC(15,2) NOT NULL,
  vencimento DATE NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE' CHECK (status IN ('PENDENTE','PAGO','VENCIDO','CANCELADO')),
  chave_pix TEXT NOT NULL,
  tipo_chave VARCHAR(20) CHECK (tipo_chave IN ('CPF','CNPJ','EMAIL','TELEFONE','ALEATORIA','QRCODE')),
  observacoes TEXT,
  pago_em TIMESTAMP,
  criado_em TIMESTAMP NOT NULL DEFAULT NOW(),
  atualizado_em TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pix_empresa ON pagamentos_pix(empresa_id);
CREATE INDEX idx_pix_loja ON pagamentos_pix(loja_id);
CREATE INDEX idx_pix_vencimento ON pagamentos_pix(vencimento);
CREATE INDEX idx_pix_status ON pagamentos_pix(status);
