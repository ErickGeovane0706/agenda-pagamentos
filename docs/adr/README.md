# Decisões de arquitetura

Registro das decisões que não são óbvias no código — as que alguém tentaria
"melhorar" sem saber o que elas estão segurando.

Cada uma segue o mesmo formato: **contexto**, **decisão**, **consequência**.
Onde existe um teste que prova a regra, ele está citado: é o que impede a
decisão de ser desfeita por engano.

| # | Decisão |
|---|---|
| [0001](0001-multi-tenancy-por-tenantcontext.md) | Isolamento entre empresas por `ThreadLocal`, não por schema |
| [0002](0002-schema-so-por-migration.md) | Schema só por migration, e o Postgres do teste é o da produção |
| [0003](0003-cadastro-verify-first.md) | Cadastro verify-first, com respostas indistinguíveis |
| [0004](0004-preco-como-fonte-unica.md) | Preço por loja, com uma fonte única e duas exceções conhecidas |
| [0005](0005-webhook-autenticado-falha-fechada.md) | Webhook de pagamento autenticado, e que falha fechada |
| [0006](0006-modulo-nao-contratado-responde-403.md) | Módulo não contratado responde 403, nunca 402 |
| [0007](0007-venda-itens-congela-preco.md) | O item de venda congela o preço; o relatório nunca lê o cadastro |
| [0008](0008-baixa-de-estoque-por-update-condicional.md) | Baixa de estoque por `UPDATE` condicional, não read-check-write |
| [0009](0009-produto-desativa-nao-apaga.md) | Produto desativa, não apaga |
| [0010](0010-nginx-re-resolve-o-backend.md) | O nginx re-resolve o backend a cada requisição |
