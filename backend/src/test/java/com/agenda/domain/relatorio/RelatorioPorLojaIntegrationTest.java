package com.agenda.domain.relatorio;

import com.agenda.domain.empresa.Empresa;
import com.agenda.domain.empresa.EmpresaRepository;
import com.agenda.domain.loja.Loja;
import com.agenda.domain.loja.LojaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * O relatório com "Todas as lojas" mostra o total no topo e a quebra por loja
 * embaixo — os dois têm de bater, centavo por centavo. Já divergiram uma vez:
 * o topo somava cancelados e a quebra não.
 * <p>
 * Contra um Postgres de verdade porque o que está sob prova são as consultas
 * agrupadas: período, status dos três tipos e, principalmente, que a loja de
 * outra empresa nunca aparece.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class RelatorioPorLojaIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:18-alpine");

    private static final LocalDate DE = LocalDate.of(2026, 10, 1);
    private static final LocalDate ATE = LocalDate.of(2026, 10, 31);
    private static final LocalDate DENTRO = LocalDate.of(2026, 10, 15);

    @Autowired private RelatorioService relatorioService;
    @Autowired private EmpresaRepository empresaRepository;
    @Autowired private LojaRepository lojaRepository;
    @Autowired private JdbcTemplate jdbc;

    private UUID empresa;
    private Loja centro;
    private Loja norte;

    @BeforeEach
    void setUp() {
        var e = empresaRepository.save(Empresa.builder().nome("Rede " + UUID.randomUUID()).build());
        empresa = e.getId();
        centro = lojaRepository.save(Loja.builder().empresa(e).nome("Centro").cor("#000000").build());
        norte = lojaRepository.save(Loja.builder().empresa(e).nome("Norte").cor("#000000").build());

        boleto(centro, "PAGO", "100.00", DENTRO);
        boleto(centro, "PENDENTE", "200.00", DENTRO);
        boleto(centro, "VENCIDO", "50.00", DENTRO);
        boleto(centro, "CANCELADO", "999.00", DENTRO);
        boleto(centro, "PENDENTE", "777.00", LocalDate.of(2026, 11, 1)); // fora do período
        pix(centro, "PAGO", "10.00");
        cheque(centro, "COMPENSADO", "30.00");
        cheque(centro, "DEVOLVIDO", "40.00");
        cheque(centro, "PENDENTE", "60.00");
        cheque(centro, "CANCELADO", "999.00");
        boleto(norte, "PENDENTE", "1000.00", DENTRO);

        // Outra empresa, mesmo período: não pode vazar para o relatório.
        var outra = empresaRepository.save(Empresa.builder().nome("Outra " + UUID.randomUUID()).build());
        var lojaDeOutra = lojaRepository.save(Loja.builder().empresa(outra).nome("Intrusa").cor("#000000").build());
        boleto(lojaDeOutra, "PENDENTE", "5000.00", DENTRO);
    }

    @Test
    @SuppressWarnings("unchecked")
    void todasAsLojas_quebraPorLojaSoDaEmpresa_eSomaIgualAoTotalDoTopo() {
        var r = relatorioService.gerar(empresa, DE, ATE, null, "TODOS", null);

        var porLoja = (List<Map<String, Object>>) r.get("porLoja");
        assertEquals(List.of("Norte", "Centro"), porLoja.stream().map(l -> l.get("nome")).toList());

        var c = porLoja.get(1);
        assertEquals(centro.getId(), c.get("lojaId"));
        assertEquals(0, new BigDecimal("140.00").compareTo((BigDecimal) c.get("valorPago")));      // 100 + 10 + 30
        assertEquals(0, new BigDecimal("350.00").compareTo((BigDecimal) c.get("valorAPagar")));    // 200 + 50 + 40 + 60
        assertEquals(0, new BigDecimal("90.00").compareTo((BigDecimal) c.get("valorEmAtraso")));   // 50 + 40

        var somaPorLoja = porLoja.stream()
            .map(l -> ((BigDecimal) l.get("valorPago")).add((BigDecimal) l.get("valorAPagar")))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, ((BigDecimal) r.get("totalGeral")).compareTo(somaPorLoja),
            "o total do topo tem de bater com a soma das lojas");
        assertEquals(0, new BigDecimal("1490.00").compareTo(somaPorLoja));
    }

    @Test
    @SuppressWarnings("unchecked")
    void filtroDeTipoEStatus_valeTambemParaAQuebraPorLoja_eContinuaBatendo() {
        var r = relatorioService.gerar(empresa, DE, ATE, null, "CHEQUE", "PENDENTE");

        var porLoja = (List<Map<String, Object>>) r.get("porLoja");
        assertEquals(1, porLoja.size());
        assertEquals(0, new BigDecimal("100.00").compareTo((BigDecimal) porLoja.get(0).get("valorAPagar"))); // 60 + 40 devolvido
        assertEquals(0, new BigDecimal("100.00").compareTo((BigDecimal) r.get("totalGeral")));
    }

    @Test
    void umaLojaSo_naoTemQuebra_eOTotalEhSoDela() {
        var r = relatorioService.gerar(empresa, DE, ATE, norte.getId(), "TODOS", null);

        assertNull(r.get("porLoja"));
        assertEquals(0, new BigDecimal("1000.00").compareTo((BigDecimal) r.get("totalGeral")));
    }

    private void boleto(Loja loja, String status, String valor, LocalDate venc) {
        jdbc.update("INSERT INTO boletos (empresa_id, loja_id, fornecedor, valor, vencimento, status) VALUES (?, ?, 'F', ?, ?, ?)",
            loja.getEmpresa().getId(), loja.getId(), new BigDecimal(valor), venc, status);
    }

    private void pix(Loja loja, String status, String valor) {
        jdbc.update("INSERT INTO pagamentos_pix (empresa_id, loja_id, fornecedor, valor, vencimento, status, chave_pix) VALUES (?, ?, 'F', ?, ?, ?, 'x')",
            loja.getEmpresa().getId(), loja.getId(), new BigDecimal(valor), DENTRO, status);
    }

    private void cheque(Loja loja, String status, String valor) {
        jdbc.update("INSERT INTO cheques (empresa_id, loja_id, fornecedor, valor, vencimento, status) VALUES (?, ?, 'F', ?, ?, ?)",
            loja.getEmpresa().getId(), loja.getId(), new BigDecimal(valor), DENTRO, status);
    }
}
