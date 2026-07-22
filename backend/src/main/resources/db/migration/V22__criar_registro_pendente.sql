-- Cadastro público aguardando confirmação de email (verify-first).
--
-- O tenant NÃO nasce aqui: empresa, loja, usuário e trial só são criados quando
-- o link do email é clicado. Assim, um bot que confirme nada deixa apenas uma
-- linha nesta tabela, apagada por job — em vez de exigir um fluxo de exclusão de
-- tenant, que é o caminho mais perigoso do sistema.
--
-- VARCHAR e não CHAR em token_hash: CHAR vira bpchar no Postgres e o
-- ddl-auto=validate recusa o mapeamento (String -> varchar). Mesmo tropeço da V21.
CREATE TABLE registro_pendente (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),

  -- Uma pergunta só no formulário: para um dono de loja pequena, "empresa" e
  -- "loja" são a mesma coisa. Este valor vira empresas.nome E lojas.nome.
  nome_negocio  VARCHAR(200) NOT NULL,

  nome_usuario  VARCHAR(200) NOT NULL,

  -- SEM unique de propósito: dois cadastros pendentes do mesmo email podem
  -- coexistir (a pessoa tenta de novo antes de confirmar). O unique de
  -- usuarios.email é o backstop na confirmação, e a violação dele é mapeada
  -- para resposta neutra — nunca 500.
  email         VARCHAR(200) NOT NULL,

  -- BCrypt já aplicado no POST. A senha em claro nunca é persistida.
  senha_hash    VARCHAR(300) NOT NULL,

  -- SHA-256 em hex (64 chars). O token cru só existe no email: um dump do banco
  -- não permite confirmar cadastro nenhum.
  token_hash    VARCHAR(64) NOT NULL UNIQUE,

  expira_em     TIMESTAMP NOT NULL,
  criado_em     TIMESTAMP NOT NULL DEFAULT NOW()
);
