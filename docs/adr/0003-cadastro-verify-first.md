# 0003 — Cadastro verify-first, com respostas indistinguíveis

## Contexto

O cadastro é público e o produto é vendido por anúncio, então o formulário é
alvo de duas coisas ao mesmo tempo: cadastro automatizado em massa e
**enumeração de contas** — descobrir quais e-mails já existem no sistema.

Enumeração raramente é o ataque; é o insumo dele. Uma lista de e-mails
confirmados vira campanha de phishing dirigida.

## Decisão

**Verify-first.** O `POST` de registro não cria usuário nem empresa. Grava um
`RegistroPendente` com token de uso único e prazo, e manda o link por e-mail.
A conta nasce quando o link é aberto.

**A resposta é a mesma, exista o e-mail ou não.** Mesmo status, mesmo corpo,
mesmo tempo. Quem já tem conta recebe um e-mail diferente — avisando que
alguém tentou cadastrar com aquele endereço — mas quem enviou o formulário
não vê diferença nenhuma.

**O hash da senha é calculado ANTES da consulta ao banco.** Esse é o detalhe
que quase sempre falta: o BCrypt custa cerca de 250 ms, e se rodasse apenas no
caminho "e-mail livre", a diferença de latência entre os dois caminhos seria
um oráculo tão bom quanto uma mensagem de erro distinta. Respostas iguais com
tempos diferentes não são respostas iguais.

A mesma regra vale para recuperação de senha.

**Dado de cobrança fica fora do cadastro.** CPF/CNPJ e celular são exigidos
só na hora de assinar. Pedir documento a quem ainda está decidindo se vai usar
o sistema é a maior fricção possível no funil, e não protege de nada.

**Todo passo do onboarding é pulável, e cada um grava a etapa no servidor.**
Sem persistir, "não trabalho com cheque" não deixa rastro nos dados e o
sistema perguntaria para sempre; o wizard também precisa retomar de onde
parou se a aba fechar.

## Consequência

Um cadastro passa a depender de e-mail entregue: se o provedor de envio cair,
ninguém entra. Em troca, não existe conta não verificada no banco, e o
formulário não responde a quem está sondando quais e-mails existem.

Provado em `RegistroServiceTest` e `SenhaResetServiceTest` — inclusive a
ordem do hash, que é o que se perde numa refatoração inocente.
