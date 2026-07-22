-- Aceite dos termos de uso e da política de privacidade, com data.
--
-- Exigência de LGPD: o consentimento precisa ser demonstrável. O checkbox no
-- formulário de cadastro não prova nada se não for persistido — é este par de
-- colunas que serve de evidência de quando e se a pessoa aceitou.
--
-- Espelha o par aceite_whatsapp/aceite_whatsapp_em que já existe desde a V1.
--
-- Nullable e default false: usuários que já existem não aceitaram nada neste
-- fluxo, e marcar todos como "aceitou" seria inventar consentimento que nunca
-- houve. Fica false com data nula, que é a verdade.
ALTER TABLE usuarios
  ADD COLUMN aceite_termos BOOLEAN NOT NULL DEFAULT false,
  ADD COLUMN aceite_termos_em TIMESTAMP;
