package com.agenda.domain.pix;

import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Construtor de consultas dinâmicas para {@link PagamentoPix}.
 * <p>
 * Os mesmos filtros de {@link com.agenda.domain.boleto.BoletoSpecification}:
 * empresa (obrigatório, multi-tenant), loja, status, intervalo de vencimento e
 * fornecedor (busca case-insensitive com LIKE).
 * </p>
 */
public class PagamentoPixSpecification {

    public static Specification<PagamentoPix> comFiltros(
            UUID empresaId, UUID lojaId, StatusPix status,
            LocalDate de, LocalDate ate, String fornecedor) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // Filtro obrigatório: isolamento multi-tenant por empresa
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
                predicates.add(cb.like(cb.lower(root.get("fornecedor")), "%" + fornecedor.toLowerCase() + "%"));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}