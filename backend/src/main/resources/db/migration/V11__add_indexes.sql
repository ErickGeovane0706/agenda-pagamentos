CREATE INDEX IF NOT EXISTS idx_boletos_loja_id ON boletos(loja_id);
CREATE INDEX IF NOT EXISTS idx_boletos_status ON boletos(status);
CREATE INDEX IF NOT EXISTS idx_boletos_vencimento ON boletos(vencimento);
CREATE INDEX IF NOT EXISTS idx_pix_loja_id ON pagamentos_pix(loja_id);
CREATE INDEX IF NOT EXISTS idx_cheques_loja_id ON cheques(loja_id);
CREATE INDEX IF NOT EXISTS idx_cheques_status ON cheques(status);
CREATE INDEX IF NOT EXISTS idx_cheques_vencimento ON cheques(vencimento);
