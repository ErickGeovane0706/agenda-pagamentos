package com.agenda.r2;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * Configura client S3 e S3Presigner apontando para Cloudflare R2
 * (compatível com S3). Endpoint no formato accountId.r2.cloudflarestorage.com,
 * região fixa "auto". Credenciais vêm de R2Properties (application.yml).
 */
@Configuration
public class R2Config {

    @Bean
    public S3Client s3Client(R2Properties props) {
        return S3Client.builder()
            .endpointOverride(URI.create(
                "https://" + props.getAccountId() + ".r2.cloudflarestorage.com"
            ))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey())
            ))
            .region(Region.of("auto"))
            .build();
    }

    @Bean
    public S3Presigner s3Presigner(R2Properties props) {
        return S3Presigner.builder()
            .endpointOverride(URI.create(
                "https://" + props.getAccountId() + ".r2.cloudflarestorage.com"
            ))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey())
            ))
            .region(Region.of("auto"))
            .build();
    }
}
