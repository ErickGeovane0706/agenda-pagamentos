-- Em que ponto do onboarding guiado o usuário parou.
--
-- Por que persistir em vez de deduzir dos dados: quase tudo daria para inferir
-- ("tem loja?", "tem preferência de notificação?"), MENOS as respostas negativas.
-- "Não trabalho com cheque" não deixa rastro nenhum no banco — sem esta coluna,
-- o sistema perguntaria isso a cada login, para sempre.
--
-- DEFAULT 'CONCLUIDO' é deliberado: usuários que já existem, e os que o admin
-- cria manualmente dentro de uma empresa, NÃO devem cair num wizard de boas-vindas.
-- Só o provisionamento do cadastro público opta explicitamente por iniciá-lo,
-- gravando a primeira etapa. Sem o default, o deploy jogaria todo mundo no wizard.
ALTER TABLE usuarios
  ADD COLUMN onboarding_etapa VARCHAR(30) NOT NULL DEFAULT 'CONCLUIDO';
