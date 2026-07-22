package com.agenda.domain.uso;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface UsoMensalEmpresaRepository
        extends JpaRepository<UsoMensalEmpresa, UsoMensalEmpresa.Chave> {

    /**
     * Soma 1 ao consumo da empresa no mês, mas só se ainda estiver abaixo do
     * teto. Devolve 1 se consumiu (o chamador pode gastar) e 0 se o teto já
     * estava atingido.
     * <p>
     * <b>É uma única instrução SQL de propósito.</b> Ler-depois-gravar abriria
     * uma janela entre a leitura e o incremento — e os dois chamadores são
     * exatamente os que correm em paralelo: o scheduler roda em toda réplica do
     * Railway e o agente processa mensagens em {@code @Async}. Com {@code ON
     * CONFLICT}, o Postgres serializa a linha e o teto não vaza, quantas
     * instâncias existirem. É a diferença entre este contador e o
     * {@code RateLimiterService}, que vive na memória de uma instância só.
     * <p>
     * Primeira query nativa do projeto: {@code ON CONFLICT ... DO UPDATE ...
     * WHERE} não existe em JPQL, e a alternativa portátil (UPDATE, se 0 tenta
     * INSERT, se violar unique volta ao UPDATE) trocaria seis linhas de SQL por
     * um laço de retentativa que ainda precisaria distinguir "linha ausente" de
     * "teto atingido". O projeto já é Postgres-only (dialeto fixo no
     * application.yml, Testcontainers com Postgres).
     */
    @Modifying
    @Query(value = """
        INSERT INTO uso_mensal_empresa (empresa_id, competencia, tipo, quantidade, atualizado_em)
             VALUES (:empresaId, :competencia, :tipo, 1, NOW())
        ON CONFLICT (empresa_id, competencia, tipo)
        DO UPDATE SET quantidade = uso_mensal_empresa.quantidade + 1,
                      atualizado_em = NOW()
              WHERE uso_mensal_empresa.quantidade < :teto
        """, nativeQuery = true)
    int consumirSeAbaixoDoTeto(@Param("empresaId") UUID empresaId,
                               @Param("competencia") String competencia,
                               @Param("tipo") String tipo,
                               @Param("teto") int teto);
}
