package com.agenda.domain.cheque;

import com.agenda.domain.empresa.Empresa;
import com.agenda.domain.empresa.EmpresaRepository;
import com.agenda.domain.loja.Loja;
import com.agenda.domain.loja.LojaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A busca da agenda acha o cheque pelo fornecedor ou pela nota fiscal/pedido.
 * Contra um Postgres de verdade porque o que está sob prova é a consulta,
 * incluindo cheque antigo, sem o campo novo.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ChequeBuscaIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired private ChequeRepository chequeRepository;
    @Autowired private EmpresaRepository empresaRepository;
    @Autowired private LojaRepository lojaRepository;

    private UUID empresa;

    @BeforeEach
    void setUp() {
        var e = empresaRepository.save(Empresa.builder().nome("Rede " + UUID.randomUUID()).build());
        empresa = e.getId();
        var loja = lojaRepository.save(Loja.builder().empresa(e).nome("Centro").cor("#000000").build());
        salvar(e, loja, "Ambev", "NF 4521/1");
        salvar(e, loja, "Coca-Cola", "Pedido 88");
        salvar(e, loja, "Seara", null);  // cheque antigo, de antes do campo
    }

    @Test
    void achaPelaNotaFiscalOuPedido_semDiferenciarMaiuscula() {
        assertEquals(List.of("Ambev"), buscar("nf 4521"));
        assertEquals(List.of("Coca-Cola"), buscar("PEDIDO 88"));
    }

    @Test
    void continuaAchandoPeloFornecedor_inclusiveChequeSemOCampoNovo() {
        assertEquals(List.of("Seara"), buscar("sear"));
    }

    private List<String> buscar(String termo) {
        return chequeRepository.findAll(ChequeSpecification.comFiltros(empresa, null, null, null, null, termo))
            .stream().map(Cheque::getFornecedor).sorted().toList();
    }

    private void salvar(Empresa e, Loja loja, String fornecedor, String nf) {
        chequeRepository.save(Cheque.builder().empresa(e).loja(loja).fornecedor(fornecedor)
            .valor(new BigDecimal("100")).vencimento(LocalDate.of(2026, 10, 15))
            .notaFiscalPedido(nf).build());
    }
}
