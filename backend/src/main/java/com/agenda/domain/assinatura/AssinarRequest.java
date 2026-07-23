package com.agenda.domain.assinatura;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Quantas lojas o cliente quer contratar ao assinar.
 * <p>
 * A quantidade só produz efeito junto com a criação da assinatura no gateway —
 * nunca isolada. Se fosse possível subir {@code lojasContratadas} por conta
 * própria, quem está em trial elevaria o número e criaria lojas de graça, e o
 * limite de 1 loja (o que transforma a 2ª em argumento de venda) deixaria de
 * existir.
 * <p>
 * O teto de 50 não é regra de negócio, é sanidade: protege contra um dedo
 * escorregando no campo e gerando uma cobrança absurda no gateway.
 */
public record AssinarRequest(
    /**
     * Obrigatório quando há corpo. Ao assinar, o corpo inteiro é opcional
     * (quem não escolhe fica com o que já tem contratado); o que não se aceita
     * é um corpo que diga "quero mudar" sem dizer para quanto.
     */
    @NotNull(message = "Informe a quantidade de lojas")
    @Min(value = 1, message = "A assinatura precisa de ao menos 1 loja")
    @Max(value = 50, message = "Para mais de 50 lojas, fale com a gente")
    Integer lojas
) {}
