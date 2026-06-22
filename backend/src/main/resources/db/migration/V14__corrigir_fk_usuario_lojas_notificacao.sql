-- A tabela usuario_lojas_notificacao foi criada referenciando usuario_id,
-- mas o JPA/Hibernate precisa referenciar a chave primária real da
-- entidade dona (preferencias_notificacao.id) para o @ElementCollection
-- funcionar de forma previsível. Como a tabela está vazia (a feature
-- ainda não tinha persistido nenhuma associação), apenas recriamos a
-- coluna sem necessidade de migrar dados.

ALTER TABLE usuario_lojas_notificacao RENAME COLUMN usuario_id TO preferencia_id;

ALTER TABLE usuario_lojas_notificacao
DROP CONSTRAINT IF EXISTS usuario_lojas_notificacao_usuario_id_fkey;

ALTER TABLE usuario_lojas_notificacao
    ADD CONSTRAINT usuario_lojas_notificacao_preferencia_id_fkey
        FOREIGN KEY (preferencia_id) REFERENCES preferencias_notificacao(id) ON DELETE CASCADE;

DROP INDEX IF EXISTS idx_usuario_lojas_notif_usuario;
CREATE INDEX idx_usuario_lojas_notif_preferencia ON usuario_lojas_notificacao(preferencia_id);