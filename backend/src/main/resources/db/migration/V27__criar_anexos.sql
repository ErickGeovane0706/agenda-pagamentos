-- Até 5 arquivos por lançamento (boleto, comprovante, pedido, nota fiscal...).
-- Antes era um só, na coluna arquivo_key de cada tabela.
--
-- Uma FK por tipo de lançamento, e exatamente uma preenchida: assim o ON DELETE
-- CASCADE apaga as linhas junto com o lançamento, o que um par "tipo + id" sem
-- FK não faria. O arquivo no R2 continua sendo apagado pelo service.

CREATE TABLE anexos (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  boleto_id UUID REFERENCES boletos(id) ON DELETE CASCADE,
  pix_id UUID REFERENCES pagamentos_pix(id) ON DELETE CASCADE,
  cheque_id UUID REFERENCES cheques(id) ON DELETE CASCADE,
  arquivo_key VARCHAR(500) NOT NULL,
  nome_arquivo VARCHAR(300),
  criado_em TIMESTAMP NOT NULL DEFAULT NOW(),
  CONSTRAINT anexos_um_lancamento CHECK (num_nonnulls(boleto_id, pix_id, cheque_id) = 1)
);

CREATE INDEX idx_anexos_boleto ON anexos (boleto_id) WHERE boleto_id IS NOT NULL;
CREATE INDEX idx_anexos_pix ON anexos (pix_id) WHERE pix_id IS NOT NULL;
CREATE INDEX idx_anexos_cheque ON anexos (cheque_id) WHERE cheque_id IS NOT NULL;

-- O arquivo que cada lançamento já tinha vira o primeiro anexo dele.
INSERT INTO anexos (boleto_id, arquivo_key, nome_arquivo, criado_em)
SELECT id, arquivo_key, nome_arquivo, atualizado_em FROM boletos WHERE arquivo_key IS NOT NULL;

INSERT INTO anexos (pix_id, arquivo_key, criado_em)
SELECT id, arquivo_key, atualizado_em FROM pagamentos_pix WHERE arquivo_key IS NOT NULL;

INSERT INTO anexos (cheque_id, arquivo_key, criado_em)
SELECT id, arquivo_key, atualizado_em FROM cheques WHERE arquivo_key IS NOT NULL;

-- As colunas antigas (arquivo_key nas três tabelas; url_arquivo e nome_arquivo em
-- boletos) ficam, sem uso, para o código anterior ainda funcionar num rollback.
-- Podem ser removidas numa migration futura.
