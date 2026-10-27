-- O cliente escolhe a ordem em que as lojas aparecem; antes era sempre por nome.
--
-- A ordem inicial de cada empresa é a alfabética de hoje, então ninguém vê a
-- lista mudar até reordenar de propósito.

ALTER TABLE lojas ADD COLUMN ordem INT NOT NULL DEFAULT 0;

UPDATE lojas l
SET ordem = o.posicao
FROM (
  SELECT id, ROW_NUMBER() OVER (PARTITION BY empresa_id ORDER BY nome) - 1 AS posicao
  FROM lojas
) o
WHERE l.id = o.id;
