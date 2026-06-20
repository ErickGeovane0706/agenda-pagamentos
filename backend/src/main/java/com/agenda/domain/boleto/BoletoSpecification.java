package com.agenda.domain.boleto;

import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BoletoSpecification {

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
                predicates.add(cb.like(cb.lower(root.get("fornecedor")), "%" + fornecedor.toLowerCase() + "%"));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}