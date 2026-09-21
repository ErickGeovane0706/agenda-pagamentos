# 0004 — Preço por loja, com uma fonte única e duas exceções conhecidas

## Contexto

O modelo de cobrança é produto único com preço por loja: um valor base que já
inclui a primeira loja, mais um valor por loja adicional. Preço muda — por
promoção, por reposicionamento, por teste de mercado.

Preço espalhado pelo código é o caminho conhecido para um cliente ver um
valor na tela, outro no contrato e um terceiro na fatura.

## Decisão

Os dois valores vivem na configuração (`assinatura.preco-base` e
`assinatura.preco-loja-adicional`). O serviço de assinatura lê de lá e o valor
sobe para a interface pelo DTO. **Nenhuma tela tem preço chumbado.**

**Duas exceções, declaradas em vez de escondidas:** a landing e os termos de
uso escrevem o número à mão, porque ali ele é texto corrido dentro de uma
frase. Mudar o preço exige mudar os três lugares — e o termo de uso é contrato
de adesão, o que torna a divergência um problema jurídico, não estético.

**O gateway exige CPF/CNPJ e celular** para criar o cliente e emitir a
cobrança. Por isso esses campos existem na empresa e são validados localmente
antes da chamada: sem conferência local, o gateway recusa e o cliente recebe
"falha na comunicação", que não diz o que corrigir. Um celular com dez dígitos
— formato de telefone fixo — já travou assinaturas exatamente assim.

## Consequência

Trocar de preço é mudar uma variável de ambiente e dois textos. Esquecer um
dos dois textos é o modo de falha que sobrou, e por isso ele está escrito no
comentário da própria configuração.
