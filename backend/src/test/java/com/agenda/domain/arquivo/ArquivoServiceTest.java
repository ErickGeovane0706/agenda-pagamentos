package com.agenda.domain.arquivo;

import com.agenda.r2.R2Properties;
import com.agenda.security.RateLimiterService;
import com.agenda.shared.TenantContext;
import com.agenda.shared.exception.TooManyRequestsException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

/**
 * O que estes testes protegem: o tipo do arquivo é decidido pelos BYTES, nunca
 * pelo que o cliente declara. Sem isso, um SVG com script passaria como imagem
 * e o R2 o devolveria com {@code image/svg+xml} — XSS armazenado.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ArquivoServiceTest {

    @Mock S3Client s3Client;
    @Mock S3Presigner s3Presigner;

    ArquivoService service;

    private static final UUID LOJA = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        var props = new R2Properties();
        props.setBucket("bucket-teste");
        service = new ArquivoService(s3Client, s3Presigner, props, new RateLimiterService());
        TenantContext.setEmpresaId(UUID.randomUUID());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static byte[] png() {
        return new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    }

    private static byte[] jpeg() {
        return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};
    }

    private static byte[] pdf() {
        return "%PDF-1.7\nconteudo".getBytes();
    }

    @Test
    void recusaSvgDisfarcadoDeImagem() {
        var svg = new MockMultipartFile("arquivo", "foto.svg", "image/svg+xml",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes());

        assertThatThrownBy(() -> service.upload(svg, TipoDocumento.BOLETO, LOJA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não permitido");
    }

    @Test
    void recusaExecutavelRenomeadoParaPng() {
        var exe = new MockMultipartFile("arquivo", "boleto.png", "image/png",
                new byte[]{'M', 'Z', (byte) 0x90, 0x00});

        assertThatThrownBy(() -> service.upload(exe, TipoDocumento.BOLETO, LOJA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não permitido");
    }

    @Test
    void gravaOTipoDetectadoNosBytes_naoOQueOClienteDeclarou() throws Exception {
        // Cliente mente: diz que é PDF, os bytes são de um PNG.
        var arquivo = new MockMultipartFile("arquivo", "x.pdf", "application/pdf", png());

        String key = service.upload(arquivo, TipoDocumento.BOLETO, LOJA);

        var req = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(req.capture(), any(RequestBody.class));

        assertThat(req.getValue().contentType()).isEqualTo("image/png");
        assertThat(key).endsWith(".png");
    }

    @Test
    void extensaoNaoVemDoNomeDoArquivo() throws Exception {
        // Nome malicioso: tentaria injetar caminho na key do bucket.
        var arquivo = new MockMultipartFile("arquivo", "foto.jpg/../../outra-pasta/evil",
                "image/jpeg", jpeg());

        String key = service.upload(arquivo, TipoDocumento.BOLETO, LOJA);

        assertThat(key).doesNotContain("..").endsWith(".jpg");
        assertThat(key).startsWith(TipoDocumento.BOLETO.getPasta() + "/" + LOJA + "/");
    }

    @Test
    void aceitaPdfDeVerdade() throws Exception {
        var arquivo = new MockMultipartFile("arquivo", "boleto.pdf", "application/pdf", pdf());

        String key = service.upload(arquivo, TipoDocumento.BOLETO, LOJA);

        assertThat(key).endsWith(".pdf");
    }

    /** A especificação do PDF permite lixo antes do %PDF- — scanners antigos usam isso. */
    @Test
    void aceitaPdfComLixoAntesDoCabecalho() throws Exception {
        byte[] miolo = pdf();
        byte[] comLixo = new byte[300 + miolo.length];
        System.arraycopy(miolo, 0, comLixo, 300, miolo.length);

        var arquivo = new MockMultipartFile("arquivo", "scan.pdf", "application/pdf", comLixo);

        String key = service.upload(arquivo, TipoDocumento.BOLETO, LOJA);

        assertThat(key).endsWith(".pdf");
    }

    /** Mas o cabeçalho não pode estar depois dos 1024 bytes iniciais. */
    @Test
    void recusaArquivoComPdfEscondidoLaAdiante() {
        byte[] miolo = pdf();
        byte[] escondido = new byte[2000 + miolo.length];
        System.arraycopy(miolo, 0, escondido, 2000, miolo.length);

        var arquivo = new MockMultipartFile("arquivo", "x.pdf", "application/pdf", escondido);

        assertThatThrownBy(() -> service.upload(arquivo, TipoDocumento.BOLETO, LOJA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não permitido");
    }

    @Test
    void barraUploadAcimaDoTetoPorEmpresa() throws Exception {
        for (int i = 0; i < 60; i++) {
            service.upload(new MockMultipartFile("arquivo", "ok.png", "image/png", png()),
                    TipoDocumento.BOLETO, LOJA);
        }

        var maisUm = new MockMultipartFile("arquivo", "ok.png", "image/png", png());
        assertThatThrownBy(() -> service.upload(maisUm, TipoDocumento.BOLETO, LOJA))
                .isInstanceOf(TooManyRequestsException.class);
    }
}
