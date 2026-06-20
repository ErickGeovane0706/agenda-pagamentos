-- V__criar_preferencias_notificacao.sql

CREATE TABLE preferencias_notificacao (
                                          id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                          usuario_id UUID NOT NULL UNIQUE REFERENCES usuarios(id) ON DELETE CASCADE,
                                          telefone_whatsapp VARCHAR(20),
                                          whatsapp_ativo BOOLEAN NOT NULL DEFAULT false,
                                          horario_1 TIME,
                                          horario_2 TIME,
                                          horario_3 TIME,
                                          horario_4 TIME,
                                          criado_em TIMESTAMP NOT NULL DEFAULT NOW(),
                                          atualizado_em TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE usuario_lojas_notificacao (
                                           usuario_id UUID NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
                                           loja_id UUID NOT NULL REFERENCES lojas(id) ON DELETE CASCADE,
                                           PRIMARY KEY (usuario_id, loja_id)
);

CREATE INDEX idx_usuario_lojas_notif_usuario ON usuario_lojas_notificacao(usuario_id);
CREATE INDEX idx_usuario_lojas_notif_loja ON usuario_lojas_notificacao(loja_id);