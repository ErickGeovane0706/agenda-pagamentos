-- O agente conversacional do WhatsApp (WhatsAppAgentService) precisa
-- identificar o usuário pelo número de telefone que mandou a mensagem,
-- a cada mensagem recebida. A coluna telefone_whatsapp pode ter o número
-- salvo com ou sem formatação ("+55 83 99999-9999" vs "5583999999999"),
-- então a busca normaliza com REGEXP_REPLACE — sem um índice funcional
-- equivalente, essa busca faria sequential scan na tabela inteira a
-- cada mensagem recebida.

CREATE INDEX idx_preferencias_notificacao_telefone_normalizado
    ON preferencias_notificacao (REGEXP_REPLACE(telefone_whatsapp, '[^0-9]', '', 'g'))
    WHERE whatsapp_ativo = true AND telefone_whatsapp IS NOT NULL;
