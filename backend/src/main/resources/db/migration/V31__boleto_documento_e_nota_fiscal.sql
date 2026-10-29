-- Número do documento e nota fiscal/pedido do boleto, pedidos pelo cliente
-- para conferir o boleto com a NF. Opcionais: os boletos já cadastrados ficam
-- sem e o cliente completa pela edição quando quiser (mesma estratégia da V29).
--
-- Texto, não número: têm zero à esquerda, barra e letra ("000123", "NF 4521/1").
ALTER TABLE boletos ADD COLUMN numero_documento VARCHAR(30);
ALTER TABLE boletos ADD COLUMN nota_fiscal_pedido VARCHAR(50);
