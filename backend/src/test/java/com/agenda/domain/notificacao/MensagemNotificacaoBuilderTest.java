package com.agenda.domain.notificacao;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MensagemNotificacaoBuilderTest {

    private final MensagemNotificacaoBuilder builder = new MensagemNotificacaoBuilder();
    private final LocalDate hoje = LocalDate.of(2026, 6, 26); // sexta-feira

    private PendenciaNotificacao pendencia(
            PendenciaNotificacao.TipoPendencia tipo, UUID lojaId, String lojaNome,
            String fornecedor, String valor, LocalDate vencimento) {
        return new PendenciaNotificacao(tipo, lojaId, lojaNome, fornecedor, new BigDecimal(valor), vencimento);
    }

    @Test
    void parametrosResumo_DeveSomarValorTotalEValorVencidoSeparadamente() {
        UUID loja1 = UUID.randomUUID();
        UUID loja2 = UUID.randomUUID();

        var vencidos = List.of(
                pendencia(PendenciaNotificacao.TipoPendencia.BOLETO, loja1, "Mercado Central",
                        "Light", "1500.00", hoje.minusDays(3))
        );
        var venceHoje = List.of(
                pendencia(PendenciaNotificacao.TipoPendencia.PIX, loja2, "Padaria do João",
                        "Trigo SA", "300.00", hoje)
        );
        var venceFimDeSemana = List.<PendenciaNotificacao>of();

        List<String> params = builder.parametrosResumo("João", vencidos, venceHoje, venceFimDeSemana);

        assertEquals("João", params.get(0));
        assertEquals("2", params.get(1));   // total de pendências
        assertEquals("2", params.get(2));   // total de lojas distintas
        assertEquals("R$ 1.800,00", params.get(3)); // valor total
        assertEquals("R$ 1.500,00", params.get(4)); // valor vencido
    }

    @Test
    void parametrosResumo_SemVencidos_DeveRetornarValorVencidoZerado() {
        UUID loja1 = UUID.randomUUID();
        var venceHoje = List.of(
                pendencia(PendenciaNotificacao.TipoPendencia.PIX, loja1, "Loja Bom Preço",
                        "Fornecedor X", "500.00", hoje)
        );

        List<String> params = builder.parametrosResumo("Maria", List.of(), venceHoje, List.of());

        assertEquals("R$ 0,00", params.get(4));
    }

    @Test
    void parametrosResumo_DeveContarLojasDistintasUmaSoVez() {
        UUID lojaUnica = UUID.randomUUID();
        var vencidos = List.of(
                pendencia(PendenciaNotificacao.TipoPendencia.BOLETO, lojaUnica, "Loja A",
                        "Fornecedor 1", "100.00", hoje.minusDays(1)),
                pendencia(PendenciaNotificacao.TipoPendencia.PIX, lojaUnica, "Loja A",
                        "Fornecedor 2", "200.00", hoje.minusDays(2))
        );

        List<String> params = builder.parametrosResumo("Carlos", vencidos, List.of(), List.of());

        assertEquals("2", params.get(1)); // 2 pendências
        assertEquals("1", params.get(2)); // mas 1 loja só
    }

    @Test
    void parametrosItem_DeveCombinarTipoEFornecedorNoSegundoParametro() {
        var item = pendencia(PendenciaNotificacao.TipoPendencia.BOLETO, UUID.randomUUID(),
                "Mercado Central", "Light", "1500.00", hoje.minusDays(3));

        List<String> params = builder.parametrosItem(item);

        assertEquals(4, params.size());
        assertEquals("Mercado Central", params.get(0));
        assertEquals("Boleto Light", params.get(1));
        assertEquals("1.500,00", params.get(2)); // sem "R$": o texto fixo do template já tem o prefixo
        assertEquals("23/06/2026", params.get(3)); // hoje (26/06/2026) - 3 dias
    }

    @Test
    void parametrosItem_Pix_DeveUsarLabelCorreto() {
        var item = pendencia(PendenciaNotificacao.TipoPendencia.PIX, UUID.randomUUID(),
                "Padaria do João", "Trigo SA", "300.00", hoje);

        List<String> params = builder.parametrosItem(item);

        assertEquals("Pix Trigo SA", params.get(1));
        assertEquals("26/06/2026", params.get(3));
    }

    @Test
    void parametrosItem_Cheque_VenceFimDeSemana_DeveFormatarDataDoFimDeSemana() {
        LocalDate sabado = hoje.plusDays(1); // hoje é sexta-feira
        var item = pendencia(PendenciaNotificacao.TipoPendencia.CHEQUE, UUID.randomUUID(),
                "Loja Bom Preço", "Reforma Vitrine", "1200.00", sabado);

        List<String> params = builder.parametrosItem(item);

        assertEquals("Cheque Reforma Vitrine", params.get(1));
        assertEquals("27/06/2026", params.get(3));
    }

    @Test
    void parametrosItem_NenhumParametro_DeveConterQuebraDeLinha() {
        var item = pendencia(PendenciaNotificacao.TipoPendencia.BOLETO, UUID.randomUUID(),
                "Loja com Nome Bem Longo de Verdade", "Fornecedor Qualquer SA",
                "12345.67", hoje.minusDays(15));

        List<String> params = builder.parametrosItem(item);

        for (String p : params) {
            assertFalse(p.contains("\n"), "Parâmetro não pode conter quebra de linha: " + p);
            assertFalse(p.contains("\t"), "Parâmetro não pode conter tabulação: " + p);
        }
    }

    @Test
    void ordenarParaEnvio_DeveManterPrioridadeVencidosHojeFimDeSemana() {
        var vencido = pendencia(PendenciaNotificacao.TipoPendencia.BOLETO, UUID.randomUUID(),
                "Loja A", "F1", "100.00", hoje.minusDays(1));
        var hojeItem = pendencia(PendenciaNotificacao.TipoPendencia.PIX, UUID.randomUUID(),
                "Loja B", "F2", "200.00", hoje);
        var fimDeSemanaItem = pendencia(PendenciaNotificacao.TipoPendencia.CHEQUE, UUID.randomUUID(),
                "Loja C", "F3", "300.00", hoje.plusDays(1));

        List<PendenciaNotificacao> ordenado = builder.ordenarParaEnvio(
                List.of(vencido), List.of(hojeItem), List.of(fimDeSemanaItem));

        assertEquals(List.of(vencido, hojeItem, fimDeSemanaItem), ordenado);
    }
}
