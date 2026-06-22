package com.agenda.domain.cheque;

import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Construtor de consultas dinâmicas para {@link Cheque}.
 * <p>
 * Mesmo padrão de {@link com.agenda.domain.boleto.BoletoSpecification} e
 * {@link com.agenda.domain.pix.PagamentoPixSpecification}: filtro
 * obrigatório por empresa + filtros opcionais (loja, status, vencimento,
 * fornecedor).
 * </p>
 */
public class ChequeSpecification {

    public static Specification<Cheque> comFiltros(
            UUID empresaId, UUID lojaId, StatusCheque status,
            LocalDate de, LocalDate ate, String fornecedor) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // Isolamento multi-tenant
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