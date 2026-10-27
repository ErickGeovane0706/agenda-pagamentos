package com.agenda.domain.arquivo;

import java.time.LocalDateTime;
import java.util.UUID;

/** Anexo para a lista do frontend. A key do R2 não sai: o acesso é pela URL assinada. */
public record AnexoDTO(UUID id, String nomeArquivo, LocalDateTime criadoEm) {
    public static AnexoDTO from(Anexo a) {
        return new AnexoDTO(a.getId(), a.getNomeArquivo(), a.getCriadoEm());
    }
}
