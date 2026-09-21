# 0001 — Isolamento entre empresas por `ThreadLocal`, não por schema

## Contexto

O sistema é multi-empresa: cada empresa tem lojas, e nenhuma pode enxergar
dado de outra. Um vazamento aqui não é um bug de funcionalidade, é um
incidente — dados financeiros de um comerciante aparecendo para outro.

As alternativas usuais são um schema (ou banco) por empresa, ou uma coluna
`empresa_id` em cada tabela com filtro na aplicação. Schema por empresa isola
melhor, mas multiplica migrations, conexões e custo de operação por cliente —
caro demais para um produto que cobra dezenas de reais por mês.

## Decisão

Uma coluna `empresa_id` em toda tabela de domínio, e um `TenantContext` com
`ThreadLocal<UUID>` preenchido pelo `JwtAuthFilter` a partir do claim
`empresaId` do token.

Duas regras que sustentam isso:

- **Toda consulta filtra por `empresa_id`** vindo do `TenantContext`, nunca
  por um id que chegou na requisição.
- **O filtro limpa o contexto no `finally`.** O pool reaproveita threads entre
  requisições; sem o `clear()`, o próximo pedido herdaria o tenant do anterior.

## Consequência

O isolamento passa a depender de disciplina de código, e não do banco. Uma
consulta nova que esqueça o filtro compila, roda e vaza — por isso existe
teste de isolamento por serviço, e não só teste de caminho feliz.

Em troca: uma migration serve todos os clientes, um pool de conexões serve
todos, e adicionar cliente custa uma linha numa tabela.
