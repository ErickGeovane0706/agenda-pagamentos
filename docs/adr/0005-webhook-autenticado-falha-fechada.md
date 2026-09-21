# 0005 — Webhook de pagamento autenticado, e que falha fechada

## Contexto

O gateway confirma pagamento por webhook. Esse endpoint é, por natureza,
público: fica fora da autenticação por JWT, porque quem chama é o gateway e
não um usuário logado.

Um webhook de pagamento sem autenticação é uma porta para ativar assinatura
forjada — basta descobrir a URL e postar um JSON dizendo "pago".

## Decisão

**Header de autenticação comparado em tempo constante.** Comparação normal de
string sai no primeiro caractere diferente, e a diferença de tempo vaza o
token caractere a caractere.

**Sem token configurado, recusa tudo.** Se a variável de ambiente não chegar
ao container, o endpoint responde erro em vez de aceitar. É a diferença entre
falhar fechado e falhar aberto: uma configuração ausente vira indisponibilidade
do webhook, nunca um webhook público.

**O status de resposta é escolhido pelo que o reenvio resolve.** O gateway
interrompe a fila ao receber não-200, então respondemos 200 em tudo que
reenviar não consertaria — evento desconhecido, empresa inexistente, payload
incompleto. Mas falha transitória, tipicamente banco fora, devolve 500 **de
propósito**: pausar a fila e receber o evento de novo é melhor que perder um
pagamento confirmado, que é irrecuperável — o gateway não reenvia o que já
recebeu 200.

## Consequência

Um 401 nesse endpoint **não significa webhook quebrado**: significa que
alguém chamou a URL sem o token, o que é rotina na internet. Confundir isso
com incidente já custou investigação.

E a fila do gateway pode ficar parada se o banco cair — que é o comportamento
desejado, não um efeito colateral.
