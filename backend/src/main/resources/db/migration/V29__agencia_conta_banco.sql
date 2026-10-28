-- Agência e conta do banco, para o cliente distinguir duas contas no mesmo
-- banco na hora de lançar o cheque. Opcionais: os bancos já cadastrados ficam
-- sem e o cliente completa pela edição quando quiser.
--
-- Texto, não número: têm zero à esquerda e dígito que pode ser "X".

ALTER TABLE bancos ADD COLUMN agencia VARCHAR(10);
ALTER TABLE bancos ADD COLUMN conta VARCHAR(20);
