package com.agenda.r2;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propriedades de configuração do Cloudflare R2 (compatível S3).
 * Preenchidas via application.yml com prefixo "r2":
 *   r2.account-id, r2.access-key, r2.secret-key, r2.bucket
 */
@Component
@ConfigurationProperties(prefix = "r2")
public class R2Properties {
    private String accountId;
    private String accessKey;
    private String secretKey;
    private String bucket;

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }
}
