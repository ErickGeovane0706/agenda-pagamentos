package com.agenda.domain.venda;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Uma linha da venda. <b>É a classe mais importante do módulo.</b>
 * <p>
 * {@code produtoNome}, {@code precoCusto} e {@code precoVenda} são <b>cópias</b>
 * do produto no instante da venda, nunca ponteiros. É a lógica da nota fiscal:
 * o documento guarda o valor praticado, não um endereço na tabela de preços.
 * <p>
 * Sem isso, todo reajuste reescreveria o histórico inteiro — comprar ovo a
 * R$ 10 em junho, vender a R$ 15, e corrigir o cadastro para R$ 12 em agosto
 * faria <b>o lucro de junho virar R$ 3 em agosto, sozinho</b>, sem bug, sem
 * erro e sem aviso.
 * <p>
 * Por isso {@code produtoId} é um <b>UUID cru e não um {@code @ManyToOne}</b>:
 * com a relação mapeada, {@code item.getProduto().getPrecoCusto()} ficaria a um
 * ponto de distância, e é exatamente essa a chamada que destrói o relatório. O
 * que não existe não pode ser chamado por engano.
 */
@Entity
@Table(name = "venda_itens")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VendaItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venda_id", nullable = false)
    private Venda venda;

    @Column(name = "produto_id", nullable = false)
    private UUID produtoId;

    @Column(name = "produto_nome", nullable = false, length = 300)
    private String produtoNome;

    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal quantidade;

    @Column(name = "preco_custo", nullable = false, precision = 15, scale = 2)
    private BigDecimal precoCusto;

    @Column(name = "preco_venda", nullable = false, precision = 15, scale = 2)
    private BigDecimal precoVenda;
}
