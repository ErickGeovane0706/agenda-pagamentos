# 0008 — Baixa de estoque por `UPDATE` condicional, não read-check-write

## Contexto

Vender dá baixa no estoque. A forma intuitiva é: ler a quantidade, conferir
num `if` se há saldo, gravar a nova quantidade.

Essa forma está errada, e o erro não aparece em teste sequencial. Dois caixas
vendendo o último item ao mesmo tempo passam **os dois** pelo `if`, porque
ambos leram antes de qualquer um gravar. O estoque vai a negativo e o sistema
vendeu mercadoria que não existe.

## Decisão

A baixa é um `UPDATE` condicional — `WHERE quantidade >= :qtd` — que devolve
o número de linhas afetadas. Zero linhas significa que não havia saldo no
instante da gravação: vira exceção de estoque insuficiente, responde `409` e
a transação volta atrás.

Quem decide se há saldo é o banco, no momento da escrita. Nunca a aplicação,
num instante anterior.

**A venda inteira é transacional.** Baixar N itens e gravar a venda precisa
ser atômico — meia venda é pior que venda nenhuma. Vale notar que o serviço de
assinatura **não** é transacional, e isso também é deliberado: lá há chamadas
HTTP ao gateway, e segurar transação aberta durante I/O de rede prende conexão
do pool pelo tempo do terceiro. Copiar um padrão do outro criaria o bug que
cada um evita.

**Cancelar também é `UPDATE` condicional** (`WHERE status = 'CONCLUIDA'`).
Sem isso, dois cliques no botão devolvem o estoque duas vezes e criam
mercadoria do nada.

**O aviso de estoque na tela é aviso, não bloqueio.** Travar no cliente daria
falso conforto e ainda poderia estar errado: entre a tela carregar e o botão
ser clicado, outro caixa pode ter vendido.

## Consequência

O estoque nunca fica negativo, mesmo sob concorrência. Provado com oito
vendas simultâneas do último item: uma passou, sete receberam `409`, o saldo
parou em zero.

O custo é que a mensagem de erro precisa ser boa — "Estoque insuficiente de
X (disponível: 2)" diz o que fazer; "erro inesperado" manda o caixa adivinhar
com o cliente esperando.
