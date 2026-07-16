-- O webhook cai no gateway_customer_id quando o pagamento vem sem
-- externalReference (assinaturas vinculadas na mão). Sem índice isso é seq scan
-- na tabela inteira a cada evento.
CREATE INDEX IF NOT EXISTS idx_assinaturas_gateway_customer_id
  ON assinaturas (gateway_customer_id);

-- Backstop no banco contra cobrança duplicada: uma subscription do gateway não
-- pode pertencer a duas empresas. A corrida entre dois "assinar" simultâneos já
-- é resolvida no UPDATE condicional do AssinaturaRepository; esta constraint é a
-- garantia de último nível, caso algum caminho futuro escape dele.
-- UNIQUE em coluna nullable não atrapalha quem ainda não assinou: no Postgres,
-- NULLs não colidem entre si.
CREATE UNIQUE INDEX IF NOT EXISTS uq_assinaturas_gateway_subscription_id
  ON assinaturas (gateway_subscription_id);
