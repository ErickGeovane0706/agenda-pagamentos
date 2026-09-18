package com.agenda.domain.venda;

import com.agenda.domain.empresa.Empresa;
import com.agenda.domain.loja.Loja;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Uma venda do PDV, com seus itens.
 * <p>
 * {@code total} e {@code custoTotal} ficam gravados aqui, e não só nos itens:
 * o relatório de período soma milhares de vendas, e somar {@code vendas} direto
 * evita {@code JOIN} com {@code venda_itens} no caminho quente. É
 * desnormalização deliberada, calculada uma vez na gravação.
 * <p>
 * {@code clienteNome} é texto livre, e continua sendo. Não existe entidade
 * Cliente, histórico por cliente nem fiado — isso é um CRM, não foi pedido, e
 * é o caminho mais curto para o projeto inchar (§5.5 do plano).
 */
@Entity
@Table(name = "vendas")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Venda {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loja_id", nullable = false)
    private Loja loja;

    @Column(name = "cliente_nome", length = 300)
    private String clienteNome;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal total;

    @Column(name = "custo_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal custoTotal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatusVenda status = StatusVenda.CONCLUIDA;

    @Column(name = "vendido_em", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime vendidoEm = LocalDateTime.now();

    /**
     * Quem vendeu. UUID cru, como em {@code auditoria}: a venda nunca precisa
     * navegar para o usuário, e um ManyToOne só adicionaria uma relação lazy
     * para carregar sem motivo.
     */
    @Column(name = "usuario_id")
    private UUID usuarioId;

    /**
     * Os itens da venda.
     * <p>
     * O {@code @BatchSize} não é otimização especulativa: sem ele a listagem é
     * N+1 na cara. A coleção é lazy, e o {@code VendaDTO} percorre os itens de
     * cada venda da página — então uma página de 30 vendas dispara 1 consulta
     * para as vendas e <b>30 consultas para os itens</b>. Medido com o SQL
     * logado: 9 vendas custavam 10 consultas.
     * <p>
     * Com o batch, o Hibernate agrupa os ids e busca os itens de todas as
     * vendas da página num {@code IN (...)} só.
     * <p>
     * Por que não {@code JOIN FETCH}: combinado com {@code Pageable}, ele
     * obriga o Hibernate a trazer tudo e paginar <b>em memória</b> — troca um
     * problema de consultas por um de memória, que é pior e mais silencioso.
     */
    @OneToMany(mappedBy = "venda", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    @Builder.Default
    private List<VendaItem> itens = new ArrayList<>();

    /** Mantém os dois lados da relação coerentes ao montar a venda. */
    public void adicionarItem(VendaItem item) {
        item.setVenda(this);
        this.itens.add(item);
    }
}
