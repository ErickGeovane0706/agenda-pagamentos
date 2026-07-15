CREATE TABLE assinaturas (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  empresa_id UUID NOT NULL UNIQUE REFERENCES empresas(id),
  status VARCHAR(20) NOT NULL,
  lojas_contratadas INT NOT NULL,
  vigente_ate DATE,
  gateway_customer_id VARCHAR(255),
  gateway_subscription_id VARCHAR(255),
  criado_em TIMESTAMP NOT NULL DEFAULT NOW(),
  atualizado_em TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Seed: toda empresa existente ganha assinatura ATIVA cobrindo as lojas que
-- já tem (mínimo 1, para empresa ainda sem loja poder criar a primeira).
-- Sem isso o deploy bloquearia os clientes que já estão no ar.
INSERT INTO assinaturas (empresa_id, status, lojas_contratadas)
SELECT e.id,
       'ATIVA',
       GREATEST((SELECT COUNT(*) FROM lojas l WHERE l.empresa_id = e.id), 1)
FROM empresas e;
