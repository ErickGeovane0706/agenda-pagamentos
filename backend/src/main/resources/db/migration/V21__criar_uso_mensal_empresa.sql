-- Consumo pago por empresa, por mês (competência 'YYYY-MM').
--
-- Existe porque o trial dá acesso a recursos que custam dinheiro de verdade
-- (template da Meta, LLM do agente, transcrição de áudio) e o cadastro público
-- deixa qualquer um abrir um trial. O teto por telefone do RateLimiterService
-- não serve aqui: ele vive em memória (zera no restart, multiplica por réplica)
-- e é por remetente, não por empresa.
--
-- Uma linha por (empresa, competência, tipo) em vez de uma coluna por tipo:
-- o consumo vira um único UPSERT parametrizado, e um tipo novo de custo não
-- exige migration.
CREATE TABLE uso_mensal_empresa (
  empresa_id    UUID        NOT NULL REFERENCES empresas(id),
  -- VARCHAR e não CHAR: CHAR vira bpchar no Postgres e o ddl-auto=validate
  -- recusa o mapeamento (String → varchar).
  competencia   VARCHAR(7)  NOT NULL,
  tipo          VARCHAR(20) NOT NULL,
  quantidade    INTEGER     NOT NULL DEFAULT 0,
  atualizado_em TIMESTAMP   NOT NULL DEFAULT NOW(),
  PRIMARY KEY (empresa_id, competencia, tipo)
);
