# 0009 — Produto desativa, não apaga

## Contexto

Produto sai de linha e o usuário quer tirá-lo da lista. `DELETE` é o reflexo.

Mas produto com venda registrada não pode sumir: o histórico referencia o
item, a chave estrangeira estoura, e — se estourasse "com sucesso" — o
relatório perderia a referência do que foi vendido.

## Decisão

Produto tem `ativo` booleano. "Excluir" desativa.

O nome é único por loja **apenas entre os ativos**, por índice parcial
(`WHERE ativo`). Sem o `WHERE`, desativar um produto e cadastrar outro com o
mesmo nome violaria unicidade, e o usuário levaria um erro incompreensível
sobre um produto que, para ele, não existe mais.

O serviço consulta duplicidade só entre ativos, espelhando o índice — a regra
vive nos dois lugares porque o banco é a última linha de defesa, não a
primeira mensagem de erro.

**A quantidade é `NUMERIC(15,3)`, nunca ponto flutuante.** Mercadoria se
vende por quilo e por dúzia, e binário não representa `0,1` exatamente. Erro
de arredondamento em dinheiro é o bug que ninguém encontra.

## Consequência

A tabela cresce para sempre, o que é irrelevante na escala deste produto e
barato comparado a perder histórico.

O comportamento de desativar-e-recriar com o mesmo nome é justamente o que
mais parece bug quando falta — e é um dos casos cobertos por teste.
