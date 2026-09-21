# 0002 — Schema só por migration, e o Postgres do teste é o da produção

## Contexto

Hibernate sabe alterar schema sozinho (`ddl-auto: update`). É cômodo em
desenvolvimento e perigoso em produção: a alteração acontece no boot, sem
revisão, sem ordem garantida e sem forma de reverter.

Há também uma armadilha mais sutil: testar contra um banco em memória (H2)
ou contra uma imagem diferente da de produção faz o teste passar e a
produção quebrar, porque o que difere não é o SQL padrão, é o comportamento.

## Decisão

- **`ddl-auto: validate`.** O Hibernate confere se o schema corresponde ao
  mapeamento e recusa subir se não corresponder. Alterar schema é privilégio
  exclusivo do Flyway.
- **Os testes de schema sobem um Postgres real** via Testcontainers, na mesma
  major da produção. O teste de contexto aplica todas as migrations do zero —
  é o único que prova que uma migration nova aplica.
- **Nada de imagem `-alpine` no banco.** A musl ignora o locale e ordena texto
  byte a byte (maiúsculas antes de minúsculas); a glibc ordena em ordem de
  dicionário, como a produção. As duas reportam o mesmo `datcollate`, então a
  diferença só aparece no resultado de um `ORDER BY` — silenciosa até não ser.

## Consequência

`mvn test` **exige Docker ligado**. Sem ele, os testes de schema falham com
`ContainerFetch`, que se parece com problema de ambiente e é fácil confundir
com "não é comigo" — e foi exatamente o que uma vez deixou uma migration
chegar à produção sem nunca ter sido executada.

O preço é esse. O que se compra é a garantia de que "os testes passaram"
inclui "o Flyway aplica e o schema bate".
