package com.agenda.domain.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository para SenhaResetToken.
 *
 * findByTokenHash: a busca é sempre pelo hash — o token cru nunca chega aqui.
 * deleteExpirados: limpeza do job diário (tokens vencidos ou já usados).
 */
public interface SenhaResetTokenRepository extends JpaRepository<SenhaResetToken, UUID> {
    Optional<SenhaResetToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("DELETE FROM SenhaResetToken t WHERE t.expiraEm < :agora OR t.usadoEm IS NOT NULL")
    int deleteExpirados(@Param("agora") LocalDateTime agora);
}
