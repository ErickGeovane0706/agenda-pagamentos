package com.agenda.domain.assinatura;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssinaturaRepository extends JpaRepository<Assinatura, UUID> {
    Optional<Assinatura> findByEmpresaId(UUID empresaId);
    Optional<Assinatura> findByGatewayCustomerId(String gatewayCustomerId);
    List<Assinatura> findByStatusInAndVigenteAteBefore(Collection<StatusAssinatura> status, LocalDate data);

    /**
     * Igual a {@link #findByEmpresaId}, mas traz a empresa junto. Necessário
     * quando a empresa é lida fora de transação (o {@code assinar} não abre
     * transação durante as chamadas ao gateway) — com o LAZY padrão e
     * {@code open-in-view: false}, o acesso estouraria LazyInitializationException.
     */
    @Query("SELECT a FROM Assinatura a JOIN FETCH a.empresa WHERE a.empresa.id = :empresaId")
    Optional<Assinatura> findByEmpresaIdComEmpresa(@Param("empresaId") UUID empresaId);

    /** Listagem do painel com a empresa junto — sem isso o DTO dispara uma query por linha (N+1). */
    @Query("SELECT a FROM Assinatura a JOIN FETCH a.empresa")
    List<Assinatura> findAllComEmpresa();

    /**
     * Guarda o customer recém-criado no gateway, assim que ele existe. Sem isso,
     * uma falha no passo seguinte (criar a subscription) deixaria o customer sem
     * registro aqui, e cada retentativa criaria outro — o Asaas aceita customers
     * duplicados com o mesmo CPF sem reclamar.
     *
     * @return 1 se gravou, 0 se outra requisição já havia gravado um (nesse caso o
     *         customer deste chamador fica órfão no gateway, o que é inofensivo:
     *         quem cobra é a subscription, não o customer).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
        UPDATE Assinatura a
           SET a.gatewayCustomerId = :customerId,
               a.atualizadoEm = CURRENT_TIMESTAMP
         WHERE a.empresa.id = :empresaId
           AND a.gatewayCustomerId IS NULL
        """)
    int vincularCustomerSeAusente(@Param("empresaId") UUID empresaId,
                                  @Param("customerId") String customerId);

    /**
     * Vincula a subscription recém-criada no gateway, mas só se a empresa ainda
     * não tiver uma. É um único UPDATE condicional, e é o que impede cobrança
     * duplicada: em dois {@code assinar} concorrentes, o banco serializa e apenas
     * um vê {@code gateway_subscription_id IS NULL}.
     * <p>
     * Grava o customer junto de propósito: o par (customer, subscription) tem de
     * ser coerente, então quem vence a corrida da subscription define os dois.
     *
     * @return 1 se vinculou (o chamador venceu), 0 se outra requisição chegou antes
     *         (o chamador precisa cancelar a subscription que criou).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
        UPDATE Assinatura a
           SET a.gatewaySubscriptionId = :subscriptionId,
               a.gatewayCustomerId = :customerId,
               a.atualizadoEm = CURRENT_TIMESTAMP
         WHERE a.empresa.id = :empresaId
           AND a.gatewaySubscriptionId IS NULL
        """)
    int vincularSubscriptionSeAusente(@Param("empresaId") UUID empresaId,
                                      @Param("subscriptionId") String subscriptionId,
                                      @Param("customerId") String customerId);
}
