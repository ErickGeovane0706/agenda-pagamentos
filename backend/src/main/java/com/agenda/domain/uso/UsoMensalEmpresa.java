package com.agenda.domain.uso;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Quanto uma empresa consumiu de um recurso pago num mês.
 * <p>
 * A escrita nunca passa por aqui — é sempre o UPSERT atômico de
 * {@link UsoMensalEmpresaRepository#consumirSeAbaixoDoTeto}. A entidade existe
 * para o repositório ter um tipo e, principalmente, para o
 * {@code ddl-auto: validate} conferir que a tabela bate com o código.
 */
@Entity
@Table(name = "uso_mensal_empresa")
@IdClass(UsoMensalEmpresa.Chave.class)
@Getter @NoArgsConstructor
public class UsoMensalEmpresa {

    @Id
    @Column(name = "empresa_id", nullable = false)
    private UUID empresaId;

    /** Mês de referência no formato {@code YYYY-MM}. */
    @Id
    @Column(nullable = false, length = 7)
    private String competencia;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoUso tipo;

    @Column(nullable = false)
    private Integer quantidade;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @Getter @NoArgsConstructor @AllArgsConstructor
    public static class Chave implements Serializable {
        private UUID empresaId;
        private String competencia;
        private TipoUso tipo;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Chave outra)) return false;
            return java.util.Objects.equals(empresaId, outra.empresaId)
                && java.util.Objects.equals(competencia, outra.competencia)
                && tipo == outra.tipo;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(empresaId, competencia, tipo);
        }
    }
}
