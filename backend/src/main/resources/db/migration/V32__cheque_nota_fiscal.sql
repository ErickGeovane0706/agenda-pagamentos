-- Nota fiscal/pedido do cheque, pedida pelo cliente depois de ter no boleto (V31).
-- Opcional: os cheques já cadastrados ficam sem e o cliente completa pela edição.
ALTER TABLE cheques ADD COLUMN nota_fiscal_pedido VARCHAR(50);
