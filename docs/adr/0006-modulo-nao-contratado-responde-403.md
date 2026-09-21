# 0006 — Módulo não contratado responde 403, nunca 402

## Contexto

O sistema tem dois bloqueios diferentes que parecem o mesmo:

1. **Assinatura em atraso** — o cliente contratou e não pagou.
2. **Módulo não habilitado** — o cliente simplesmente não tem aquele módulo.

O primeiro usa `402 Payment Required`, e o frontend reage a esse status
redirecionando para a tela de pagamento. É o comportamento certo para
inadimplência.

## Decisão

O gate do módulo responde **403**, e não 402.

Não-contratação não é inadimplência. Mandar para uma tela de pagamento quem
não deve nada é pior que negar: ele paga o que já paga, nada muda, e ninguém
entende por quê.

**O gate bloqueia leitura também.** Só barrar escrita deixaria listar dados de
um módulo não contratado — e "só leitura" de um módulo que não existe para
aquela empresa não é meio-acesso, é acesso.

**A regra não é repetida no frontend.** Não há guarda de rota: quem digitar a
URL do módulo sem tê-lo recebe 403 na primeira chamada. Repetir a regra no
cliente daria dois lugares para ela divergir, e o que vale é o do servidor.

**A bifurcação de navegação só aparece para quem tem o módulo.** O desenho
original mostrava a escolha sempre; sem o módulo ela teria um card único, ou
seja, um clique a mais para todo cliente que não o contratou. Módulo novo não
pode degradar o que já funciona.

## Consequência

Dois filtros parecidos convivem no código, e a diferença entre eles é um
número de status. Quem mexer em um e "padronizar" o outro reintroduz o bug —
por isso a razão está escrita no próprio filtro, e não só aqui.
