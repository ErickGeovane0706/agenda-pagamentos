CREATE TABLE webhook_evento_processado (
    id UUID PRIMARY KEY,
    origem VARCHAR(20) NOT NULL,
    evento_id VARCHAR(255) NOT NULL,
    processado_em TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_webhook_evento UNIQUE (origem, evento_id)
);
