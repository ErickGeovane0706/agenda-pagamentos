package com.agenda.domain.assinatura;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * O que a própria empresa vê da sua assinatura, na tela de contratação.
 * <p>
 * Deliberadamente menor que {@link AssinaturaDTO}: sem id, sem empresaId e sem
 * {@code gatewayCustomerId} — identificadores do painel do MASTER e do gateway
 * não têm uso na tela do cliente, e o que não é enviado não vaza.
 * <p>
 * Os preços viajam junto porque são configuração do servidor
 * ({@code assinatura.preco-*}): chumbá-los no frontend faria a tela mentir no
 * dia em que o preço mudasse por variável de ambiente.
 */
public record MinhaAssinaturaDTO(
    StatusAssinatura status,
    int lojasContratadas,
    LocalDate vigenteAte,
    BigDecimal precoBase,
    BigDecimal precoLojaAdicional,

    /**
     * Se CPF/CNPJ e telefone já estão na empresa. Um booleano, e não os valores:
     * a tela só precisa saber se ainda tem de perguntar, e documento é PII que
     * não ganha nada em trafegar de volta.
     */
    boolean dadosCobrancaCompletos,

    /**
     * Se já existe assinatura criada no gateway. Decide o caminho da tela:
     * sem ela, contratar mais lojas é parte de assinar; com ela, é uma
     * alteração de valor da assinatura que já corre.
     */
    boolean assinaturaIniciada,

    /**
     * Módulo de Estoque/PDV liberado para esta empresa. Viaja aqui, e não num
     * endpoint próprio, porque este já é o "o que a minha empresa pode fazer"
     * que o frontend consulta — um endpoint novo para um booleano seria pior.
     * É o que decide se a tela de escolher loja desenha o caminho do PDV.
     */
    boolean pdvHabilitado
) {}
