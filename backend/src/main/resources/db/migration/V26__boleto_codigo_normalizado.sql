-- Chave para avisar boleto duplicado. O codigo_barras é texto livre (44 da câmera,
-- 47/48 do papel, com ou sem pontos); aqui fica sempre o código de barras de 44.
-- Nas linhas novas quem preenche é a entidade (Boleto#prePersist/preUpdate, via
-- CodigoBoleto); o UPDATE abaixo repete a mesma conversão para as que já existem.
--
-- Sem UNIQUE de propósito: duplicata é aviso, o usuário pode confirmar — e uma
-- trava faria esta migration falhar se produção já tiver repetidos.

ALTER TABLE boletos ADD COLUMN codigo_normalizado VARCHAR(44);

UPDATE boletos b
SET codigo_normalizado = CASE length(n.d)
        WHEN 44 THEN n.d
        WHEN 47 THEN substr(n.d, 1, 4) || substr(n.d, 33, 1) || substr(n.d, 34, 14)
                  || substr(n.d, 5, 5) || substr(n.d, 11, 10) || substr(n.d, 22, 10)
        WHEN 48 THEN substr(n.d, 1, 11) || substr(n.d, 13, 11)
                  || substr(n.d, 25, 11) || substr(n.d, 37, 11)
    END
FROM (SELECT id, regexp_replace(codigo_barras, '\D', '', 'g') AS d
      FROM boletos WHERE codigo_barras IS NOT NULL) n
WHERE b.id = n.id;

CREATE INDEX idx_boletos_empresa_codigo_normalizado
    ON boletos (empresa_id, codigo_normalizado)
    WHERE codigo_normalizado IS NOT NULL;
