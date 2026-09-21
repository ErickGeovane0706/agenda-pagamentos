# 0007 — O item de venda congela o preço; o relatório nunca lê o cadastro

## Contexto

O módulo de estoque calcula lucro: receita menos custo, por período e por
produto. Produto tem preço de custo e preço de venda no cadastro, e esses
preços **mudam** — fornecedor reajusta, margem é revista.

O caminho natural é o relatório ler o preço do produto via `JOIN`. Ele está a
um ponto de distância e parece certo.

## Decisão

`venda_itens` **copia** nome, preço de custo e preço de venda no instante da
venda. O relatório lê do item, e **nunca** do cadastro.

É a lógica da nota fiscal: o documento guarda o valor praticado.

Três coisas defendem a regra, e nenhuma é comentário solto:

- **`produtoId` no item é um `UUID` cru, não um `@ManyToOne`.** Com a relação
  mapeada, `item.getProduto().getPrecoCusto()` ficaria a um ponto de
  distância — e é exatamente essa chamada que reescreve o lucro do passado.
  O que não existe não pode ser chamado por engano.
- O aviso está dentro da própria migration e do repositório, onde quem for
  mexer vai ler.
- Um teste de integração roda o cenário contra um Postgres real.

**Margem de período sem venda é nula, não zero.** Zero por cento é um negócio
que vendeu sem lucro; não vender é outra coisa. Devolver `0` ali seria
mentira aritmeticamente conveniente.

## Consequência

Sem isso: comprar um item a R$ 10 em junho, vender a R$ 15, e reajustar o
cadastro para R$ 12 em agosto faria **o lucro de junho virar R$ 3 sozinho** —
sem bug, sem erro, sem nada no log. O passado se reescreveria a cada
atualização de cadastro.

O teste que prova isso vende com custo 10, muda o produto para 12 e confere
que o lucro continua 5. **Se ele ficar vermelho com lucro 3,00, alguém passou
a ler preço do cadastro numa agregação.**
