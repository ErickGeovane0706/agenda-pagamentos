package com.agenda.domain.boleto;

import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Fábrica de {@link Specification} para consultas dinâmicas de {@link Boleto}.
 * <p>
 * Todos os filtros são opcionais e combinados com AND.
 * O filtro {@code empresaId} é sempre aplicado (isolamento multi‑tenant).
 * </p>
 */
public class BoletoSpecification {

    /**
     * Monta predicados dinâmicos: empresa (obrigatório), loja, status,
     * intervalo de vencimento {@code [de, ate]} e busca textual por fornecedor,
     * número do documento ou nota fiscal/pedido
     * (case‑insensitive, LIKE %termo%).
     */
    public static Specification<Boleto> comFiltros(
            UUID empresaId, UUID lojaId, StatusBoleto status,
            LocalDate de, LocalDate ate, String fornecedor) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("empresa").get("id"), empresaId));

            if (lojaId != null) {
                predicates.add(cb.equal(root.get("loja").get("id"), lojaId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (de != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("vencimento"), de));
            }
            if (ate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("vencimento"), ate));
            }
            if (fornecedor != null && !fornecedor.isBlank()) {
                // O mesmo campo de busca acha pelo número do documento e pela NF/pedido.
                String termo = "%" + fornecedor.toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("fornecedor")), termo),
                    cb.like(cb.lower(root.get("numeroDocumento")), termo),
                    cb.like(cb.lower(root.get("notaFiscalPedido")), termo)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}