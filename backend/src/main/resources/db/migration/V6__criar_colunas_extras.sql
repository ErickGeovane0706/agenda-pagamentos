CREATE TABLE colunas_extras (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  empresa_id UUID NOT NULL REFERENCES empresas(id),
  tipo_pagamento VARCHAR(20) NOT NULL CHECK (tipo_pagamento IN ('BOLETO','PIX','CHEQUE')),
  nome VARCHAR(100) NOT NULL,
  tipo_dado VARCHAR(20) NOT NULL DEFAULT 'TEXTO' CHECK (tipo_dado IN ('TEXTO','NUMERO','DATA','BOOLEANO')),
  obrigatorio BOOLEAN NOT NULL DEFAULT false,
  ordem INT NOT NULL DEFAULT 0,
  ativo BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE valores_extras (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  coluna_id UUID NOT NULL REFERENCES colunas_extras(id),
  registro_id UUID NOT NULL,
  tipo_pagamento VARCHAR(20) NOT NULL,
  valor TEXT
);

CREATE INDEX idx_valores_extras_registro ON valores_extras(registro_id);
CREATE INDEX idx_colunas_extras_empresa ON colunas_extras(empresa_id);
