-- PIX criados juntos pelo "PIX recorrente" (ex.: aluguel de varios meses) compartilham
-- este id, para que depois se possa agir sobre o grupo. Null no PIX avulso.
ALTER TABLE pagamentos_pix ADD COLUMN recorrencia_id UUID;
