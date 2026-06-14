CREATE TABLE bancos (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  empresa_id UUID NOT NULL REFERENCES empresas(id),
  nome VARCHAR(200) NOT NULL,
  codigo VARCHAR(10),
  ativo BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE cheques (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  empresa_id UUID NOT NULL REFERENCES empresas(id),
  loja_id UUID NOT NULL REFERENCES lojas(id),
  banco_id UUID REFERENCES bancos(id),
  fornecedor VARCHAR(300) NOT NULL,
  valor NUMERIC(15,2) NOT NULL,
  vencimento DATE NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE' CHECK (status IN ('PENDENTE','COMPENSADO','DEVOLVIDO','CANCELADO')),
  numero_cheque VARCHAR(50),
  observacoes TEXT,
  compensado_em TIMESTAMP,
  criado_em TIMESTAMP NOT NULL DEFAULT NOW(),
  atualizado_em TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cheques_empresa ON cheques(empresa_id);
CREATE INDEX idx_cheques_loja ON cheques(loja_id);
CREATE INDEX idx_cheques_vencimento ON cheques(vencimento);
CREATE INDEX idx_cheques_status ON cheques(status);
CREATE INDEX idx_bancos_empresa ON bancos(empresa_id);
