package com.agenda.domain.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository de {@link RegistroPendente}.
 * <p>
 * A busca é sempre pelo hash — o token cru nunca chega até aqui. Não existe
 * consulta por email de propósito: responder qualquer coisa baseada na
 * existência de um cadastro pendente transformaria o endpoint num oráculo de
 * quem está se cadastrando.
 */
public interface RegistroPendenteRepository extends JpaRepository<RegistroPendente, UUID> {

    Optional<RegistroPendente> findByTokenHash(String tokenHash);

    /**
     * Limpeza do job diário. Só precisa varrer os vencidos: os confirmados já
     * foram apagados no momento da confirmação.
     */
    @Modifying
    @Query("DELETE FROM RegistroPendente r WHERE r.expiraEm < :agora")
    int deleteExpirados(@Param("agora") LocalDateTime agora);
}
