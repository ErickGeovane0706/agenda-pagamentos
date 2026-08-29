package com.agenda.domain.assinatura;

import java.time.LocalDate;
import java.util.UUID;

public record AssinaturaDTO(
    UUID id,
    UUID empresaId,
    String empresaNome,
    StatusAssinatura status,
    int lojasContratadas,
    LocalDate vigenteAte,
    String gatewayCustomerId,

    /**
     * Se já existe subscription no gateway. É o que separa, no painel, uma
     * alteração que mexe em dinheiro de uma que não mexe: em {@code atualizar},
     * tanto o cancelamento quanto o recálculo de valor estão atrás de
     * {@code preenchido(subscriptionId)}. Sem ela — o caso do trial — mudar
     * lojas é gravação local e nada é cobrado.
     * <p>
     * Booleano, e não o id: o painel só precisa saber se existe.
     */
    boolean assinaturaIniciada
) {
    public static AssinaturaDTO from(Assinatura a) {
        return new AssinaturaDTO(
            a.getId(),
            a.getEmpresa().getId(),
            a.getEmpresa().getNome(),
            a.getStatus(),
            a.getLojasContratadas(),
            a.getVigenteAte(),
            a.getGatewayCustomerId(),
            a.getGatewaySubscriptionId() != null && !a.getGatewaySubscriptionId().isBlank());
    }
}
