package com.agenda.domain.boleto;

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
 * A busca da agenda acha o boleto pelo fornecedor, pelo número do documento ou
 * pela nota fiscal/pedido — o cliente confere o boleto com a NF na mão.
 * Contra um Postgres de verdade porque o que está sob prova é a consulta,
 * incluindo boleto antigo, sem os campos novos.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class BoletoBuscaIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired private BoletoRepository boletoRepository;
    @Autowired private EmpresaRepository empresaRepository;
    @Autowired private LojaRepository lojaRepository;

    private UUID empresa;

    @BeforeEach
    void setUp() {
        var e = empresaRepository.save(Empresa.builder().nome("Rede " + UUID.randomUUID()).build());
        empresa = e.getId();
        var loja = lojaRepository.save(Loja.builder().empresa(e).nome("Centro").cor("#000000").build());
        salvar(e, loja, "Ambev", "000123", "NF 4521/1");
        salvar(e, loja, "Coca-Cola", "777", "Pedido 88");
        salvar(e, loja, "Seara", null, null);  // boleto antigo, de antes dos campos
    }

    @Test
    void achaPeloNumeroDoDocumento() {
        assertEquals(List.of("Ambev"), buscar("00012"));
    }

    @Test
    void achaPelaNotaFiscalOuPedido_semDiferenciarMaiuscula() {
        assertEquals(List.of("Ambev"), buscar("nf 4521"));
        assertEquals(List.of("Coca-Cola"), buscar("PEDIDO 88"));
    }

    @Test
    void continuaAchandoPeloFornecedor_inclusiveBoletoSemOsCamposNovos() {
        assertEquals(List.of("Seara"), buscar("sear"));
    }

    private List<String> buscar(String termo) {
        return boletoRepository.findAll(BoletoSpecification.comFiltros(empresa, null, null, null, null, termo))
            .stream().map(Boleto::getFornecedor).sorted().toList();
    }

    private void salvar(Empresa e, Loja loja, String fornecedor, String doc, String nf) {
        boletoRepository.save(Boleto.builder().empresa(e).loja(loja).fornecedor(fornecedor)
            .valor(new BigDecimal("100")).vencimento(LocalDate.of(2026, 10, 15))
            .numeroDocumento(doc).notaFiscalPedido(nf).build());
    }
}
