package com.agenda.domain.arquivo;

import com.agenda.r2.R2Properties;
import com.agenda.security.RateLimiterService;
import com.agenda.shared.TenantContext;
import com.agenda.shared.exception.TooManyRequestsException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Upload/download/exclusão de arquivos no Cloudflare R2 (compatível com S3).
 * Usado para anexar boletos, comprovantes de PIX e imagens de cheques.
 * <p>
 * <b>Nada vindo do cliente é levado a sério.</b> Tanto o {@code Content-Type}
 * do multipart quanto o nome do arquivo são escolhidos por quem envia, e ambos
 * acabariam na resposta que o R2 devolve ao navegador. Um SVG declarado como
 * {@code image/svg+xml} passa em qualquer checagem de "começa com image/", e o
 * navegador executa o script que ele carrega — XSS armazenado. Por isso o tipo
 * é decidido pelos BYTES do arquivo ({@link #detectarTipo}), e a extensão sai
 * desse tipo, não do nome original.
 * <p>
 * O bucket é privado: o acesso é sempre por URL assinada de curta duração.
 */
@Service
@RequiredArgsConstructor
public class ArquivoService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final R2Properties props;
    private final RateLimiterService rateLimiter;

    private static final long TAMANHO_MAXIMO_BYTES = 10 * 1024 * 1024;

    /**
     * Validade da URL assinada. Ela é um "portador": quem tiver o link acessa o
     * arquivo sem login enquanto ela valer. Como o uso real é abrir o documento
     * na hora, minutos bastam — uma hora só ampliaria a janela de um link colado
     * por engano num grupo de WhatsApp.
     */
    private static final Duration VALIDADE_URL = Duration.ofMinutes(15);

    /**
     * Teto de uploads por empresa. Cada upload é armazenamento pago na nossa
     * conta do R2, e nada impediria uma conta autenticada de subir 10 MB em
     * looping. O teto é folgado de propósito: cadastrar dezenas de boletos num
     * dia de trabalho é uso normal, e o limite só existe para cortar o abuso
     * automatizado.
     */
    private static final int UPLOAD_MAX_POR_EMPRESA = 60;
    private static final Duration UPLOAD_JANELA = Duration.ofMinutes(10);

    /** Únicos tipos aceitos, e a extensão de cada um. */
    private static final Map<String, String> EXTENSAO_POR_TIPO = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "application/pdf", "pdf"
    );

    public String upload(MultipartFile file, TipoDocumento tipo, UUID lojaId) throws IOException {
        UUID empresaId = TenantContext.getEmpresaId();
        if (!rateLimiter.tentarConsumir("upload:" + empresaId, UPLOAD_MAX_POR_EMPRESA, UPLOAD_JANELA)) {
            throw new TooManyRequestsException("Muitos arquivos enviados em pouco tempo. Aguarde alguns minutos.");
        }

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Arquivo vazio");
        }
        if (file.getSize() > TAMANHO_MAXIMO_BYTES) {
            throw new IllegalArgumentException("Arquivo maior que 10MB");
        }

        byte[] conteudo = file.getBytes();
        String tipoReal = detectarTipo(conteudo);
        if (tipoReal == null) {
            throw new IllegalArgumentException("Tipo de arquivo não permitido. Use JPEG, PNG, WEBP ou PDF.");
        }

        String key = tipo.getPasta() + "/" + lojaId + "/" +
                     UUID.randomUUID() + "." + EXTENSAO_POR_TIPO.get(tipoReal);

        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket(props.getBucket())
                .key(key)
                .contentType(tipoReal)
                .contentLength((long) conteudo.length)
                .build(),
            RequestBody.fromBytes(conteudo)
        );

        return key;
    }

    /**
     * URL assinada para visualizar o arquivo.
     * <p>
     * O tipo da resposta é imposto AQUI, e não herdado do objeto, para
     * neutralizar também o que já está no bucket: arquivos enviados antes desta
     * validação foram gravados com o Content-Type que o cliente mandou, e um
     * deles poderia ser um SVG com script. Extensão desconhecida cai em
     * download forçado — o navegador nunca renderiza o que não reconhecemos.
     */
    public String gerarUrlTemporaria(String key) {
        if (key == null || key.isBlank()) return null;

        String tipo = tipoPelaExtensao(key);
        String contentType = tipo != null ? tipo : "application/octet-stream";
        String disposicao = tipo != null ? "inline" : "attachment";

        return s3Presigner.presignGetObject(r -> r
            .signatureDuration(VALIDADE_URL)
            .getObjectRequest(g -> g
                .bucket(props.getBucket())
                .key(key)
                .responseContentType(contentType)
                .responseContentDisposition(disposicao))
        ).url().toString();
    }

    public void deletar(String key) {
        if (key == null || key.isBlank()) return;

        s3Client.deleteObject(DeleteObjectRequest.builder()
            .bucket(props.getBucket())
            .key(key)
            .build());
    }

    /**
     * Identifica o tipo pela assinatura dos primeiros bytes. Devolve {@code null}
     * se não for um dos quatro formatos aceitos — o que também barra executável
     * renomeado, SVG, HTML e qualquer outra coisa que o cliente jure ser imagem.
     */
    private String detectarTipo(byte[] b) {
        if (b.length >= 3
                && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 8
                && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && (b[4] & 0xFF) == 0x0D && (b[5] & 0xFF) == 0x0A
                && (b[6] & 0xFF) == 0x1A && (b[7] & 0xFF) == 0x0A) {
            return "image/png";
        }
        // WEBP é um container RIFF: "RIFF" + 4 bytes de tamanho + "WEBP".
        if (b.length >= 12
                && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        if (b.length >= 5
                && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-') {
            return "application/pdf";
        }
        return null;
    }

    private String tipoPelaExtensao(String key) {
        int ponto = key.lastIndexOf('.');
        if (ponto < 0) return null;
        String ext = key.substring(ponto + 1).toLowerCase();
        return EXTENSAO_POR_TIPO.entrySet().stream()
                .filter(e -> e.getValue().equals(ext))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }
}
