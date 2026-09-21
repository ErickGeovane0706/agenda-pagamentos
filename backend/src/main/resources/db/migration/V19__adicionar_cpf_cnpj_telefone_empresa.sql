-- Dados do responsável pela empresa exigidos pelo gateway (Asaas) para criar o
-- customer e emitir Pix/boleto: cpfCnpj + mobilePhone. Ver docs/adr/0004.
--
-- Nullable de propósito: empresas já existentes não possuem esses dados e não
-- devem quebrar no deploy; os campos só são obrigatórios no momento de assinar
-- via API (validação na aplicação). cpf_cnpj guarda SOMENTE dígitos (11=CPF,
-- 14=CNPJ) — a normalização é feita na aplicação e o CHECK abaixo é defesa em
-- profundidade contra dado malformado.
--
-- PII sob LGPD: não logar em claro nem expor em respostas fora do próprio tenant
-- ou do MASTER.
ALTER TABLE empresas
  ADD COLUMN cpf_cnpj VARCHAR(14),
  ADD COLUMN telefone VARCHAR(20);

ALTER TABLE empresas
  ADD CONSTRAINT chk_empresas_cpf_cnpj
  CHECK (cpf_cnpj IS NULL OR cpf_cnpj ~ '^[0-9]{11}$' OR cpf_cnpj ~ '^[0-9]{14}$');
