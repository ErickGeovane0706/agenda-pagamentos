package com.agenda.domain.arquivo;

import com.agenda.r2.R2Properties;
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
import java.util.List;
import java.util.UUID;

/**
 * Upload/download/exclusão de arquivos no Cloudflare R2
 * (compatível com S3). Usado para anexar boletos, comprovantes
 * de PIX e imagens de cheques. Apenas imagens e PDF (máx 10 MB).
 * Gera URLs temporárias de 1 hora para visualização segura.
 */
@Service
@RequiredArgsConstructor
public class ArquivoService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final R2Properties props;

    public String upload(MultipartFile file, TipoDocumento tipo, UUID lojaId) throws IOException {
        validarArquivo(file);

        String extensao = getExtensao(file.getOriginalFilename());
        String key = tipo.getPasta() + "/" + lojaId + "/" +
                     UUID.randomUUID() + "." + extensao;

        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket(props.getBucket())
                .key(key)
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .build(),
            RequestBody.fromBytes(file.getBytes())
        );

        return key;
    }

    public String gerarUrlTemporaria(String key) {
        if (key == null || key.isBlank()) return null;

        return s3Presigner.presignGetObject(r -> r
            .signatureDuration(Duration.ofHours(1))
            .getObjectRequest(g -> g
                .bucket(props.getBucket())
                .key(key))
        ).url().toString();
    }

    public void deletar(String key) {
        if (key == null || key.isBlank()) return;

        s3Client.deleteObject(DeleteObjectRequest.builder()
            .bucket(props.getBucket())
            .key(key)
            .build());
    }

    private void validarArquivo(MultipartFile file) {
        if (file == null || file.isEmpty())
            throw new IllegalArgumentException("Arquivo vazio");
        if (file.getSize() > 10 * 1024 * 1024)
            throw new IllegalArgumentException("Arquivo maior que 10MB");
        String tipo = file.getContentType();
        if (tipo == null || (!tipo.startsWith("image/") && !tipo.equals("application/pdf")))
            throw new IllegalArgumentException("Tipo de arquivo não permitido. Use PDF ou imagem.");
    }

    private String getExtensao(String nome) {
        if (nome == null || !nome.contains(".")) return "bin";
        return nome.substring(nome.lastIndexOf('.') + 1).toLowerCase();
    }
}
