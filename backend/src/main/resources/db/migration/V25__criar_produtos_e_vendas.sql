-- Módulo de Estoque e PDV. Nasce DESLIGADO: em produção ninguém vê nada até o
-- MASTER ligar o flag por empresa. O deploy desta migration é inerte.
--
-- As tabelas sobem todas de uma vez, antes do código que as usa (fases 2 e 3),
-- porque tabela vazia não faz mal e evita três migrations para um módulo só.

-- ============ 1. Flag do módulo ============
ALTER TABLE empresas
    ADD COLUMN pdv_habilitado BOOLEAN NOT NULL DEFAULT FALSE;

-- ============ 2. Produtos ============
CREATE TABLE produtos (
    id            UUID PRIMARY KEY,
    empresa_id    UUID NOT NULL REFERENCES empresas(id),
    loja_id       UUID NOT NULL REFERENCES lojas(id),
    nome          VARCHAR(300)   NOT NULL,
    -- NUMERIC(15,3) e nunca double: gelo se vende por quilo e ovo por dúzia, e
    -- ponto flutuante binário não representa 0,1 exatamente — erro de
    -- arredondamento em dinheiro é o bug que ninguém encontra.
    quantidade    NUMERIC(15,3)  NOT NULL DEFAULT 0,
    preco_custo   NUMERIC(15,2)  NOT NULL,
    preco_venda   NUMERIC(15,2)  NOT NULL,
    ativo         BOOLEAN        NOT NULL DEFAULT TRUE,
    criado_em     TIMESTAMP      NOT NULL DEFAULT NOW(),
    atualizado_em TIMESTAMP,
    -- Última linha de defesa: a regra também está no Java, mas se um dia um
    -- UPDATE manual ou um caminho novo esquecê-la, o banco recusa.
    CONSTRAINT ck_produto_qtd_nao_negativa CHECK (quantidade >= 0),
    CONSTRAINT ck_produto_precos_nao_negativos
        CHECK (preco_custo >= 0 AND preco_venda >= 0)
);

CREATE INDEX idx_produtos_loja ON produtos (empresa_id, loja_id, ativo);

-- Nome único por loja, SÓ entre os ativos. Sem o WHERE, desativar "Ovo branco"
-- e criar outro com o mesmo nome estouraria violação de unicidade e o usuário
-- levaria um erro incompreensível.
CREATE UNIQUE INDEX uq_produto_nome_ativo
    ON produtos (loja_id, LOWER(nome)) WHERE ativo;

-- ============ 3. Vendas ============
CREATE TABLE vendas (
    id           UUID PRIMARY KEY,
    empresa_id   UUID NOT NULL REFERENCES empresas(id),
    loja_id      UUID NOT NULL REFERENCES lojas(id),
    cliente_nome VARCHAR(300),
    -- total e custo_total gravados aqui, e não só nos itens: o relatório de
    -- período soma milhares de vendas, e somar direto evita JOIN com
    -- venda_itens no caminho quente. Desnormalização deliberada, calculada uma
    -- vez na gravação.
    total        NUMERIC(15,2) NOT NULL,
    custo_total  NUMERIC(15,2) NOT NULL,
    status       VARCHAR(20)   NOT NULL DEFAULT 'CONCLUIDA',
    vendido_em   TIMESTAMP     NOT NULL DEFAULT NOW(),
    usuario_id   UUID REFERENCES usuarios(id)
);

-- Ordem das colunas igual à da query do relatório (empresa + loja + status +
-- faixa de data). Índice em ordem diferente não é usado para a faixa.
CREATE INDEX idx_vendas_relatorio
    ON vendas (empresa_id, loja_id, status, vendido_em);

-- ============ 4. Itens ============
-- preco_custo, preco_venda e produto_nome são CÓPIAS do produto no instante da
-- venda, não ponteiros. É a lógica da nota fiscal: o documento guarda o valor
-- praticado. Sem isso, todo reajuste de preço reescreveria o histórico inteiro
-- e o lucro de junho mudaria em agosto, sozinho, sem erro nenhum.
CREATE TABLE venda_itens (
    id           UUID PRIMARY KEY,
    venda_id     UUID NOT NULL REFERENCES vendas(id) ON DELETE CASCADE,
    -- produtos NÃO tem cascade de propósito: apagar produto nunca pode apagar
    -- histórico de venda.
    produto_id   UUID NOT NULL REFERENCES produtos(id),
    produto_nome VARCHAR(300)  NOT NULL,
    quantidade   NUMERIC(15,3) NOT NULL,
    preco_custo  NUMERIC(15,2) NOT NULL,
    preco_venda  NUMERIC(15,2) NOT NULL,
    CONSTRAINT ck_item_qtd_positiva CHECK (quantidade > 0)
);

CREATE INDEX idx_venda_itens_venda ON venda_itens (venda_id);
