# Agenda de Pagamentos

Sistema de gestão de contas a pagar para redes com múltiplas lojas: boletos, PIX e cheques
num só lugar, com lembretes e consultas por WhatsApp.

O diferencial do projeto é o **agente de WhatsApp**: o usuário pergunta *"quanto tenho pra pagar
essa semana?"* — por texto ou por áudio — e recebe a resposta pronta, sem abrir o app.

---

## Stack

**Backend** — Java 21, Spring Boot 3.3.5, PostgreSQL, Flyway, Spring Security (JWT),
Bucket4j (rate limiting), AWS SDK S3 (Cloudflare R2), Apache POI (exportação `.xlsx`).

**Frontend** — React 18, TypeScript, Vite 5, Tailwind, TanStack Query, Zustand,
React Hook Form, React Router.

**Integrações** — WhatsApp Cloud API (Meta), Anthropic Claude (classificação de intenção),
OpenAI Whisper (transcrição de áudio), Cloudflare R2 (arquivos).

**Deploy** — Railway (Dockerfile), webhook exposto via nginx.

---

## Funcionalidades

### Agenda de pagamentos
Boletos, pagamentos PIX e cheques, cada um com loja, fornecedor, valor, vencimento, status
(pendente / pago / vencido) e anexo opcional (imagem ou PDF do documento). Colunas extras
configuráveis por empresa. Filtros por loja, status, período e fornecedor.

### Leitor de código de barras
Três formas de capturar o código de um boleto, no `ModalLeitorCodigo`:

- **Câmera** (celular) — leitura ao vivo com ZXing. A tela gira para paisagem antes de a câmera
  abrir, o que permite aproximar o aparelho do papel e ler em poucos segundos.
- **Imagem** — OCR com Tesseract.js.
- **PDF** — extrai o texto embutido e, se não houver, cai em OCR da página escolhida.

Reconhece boleto bancário (47 dígitos), convênio/concessionária, DARF, GPS/INSS, DAS/MEI,
GNRE, GRU, multas e código de barras puro (44 dígitos).

### Agente de WhatsApp
Webhook recebe a mensagem, valida a assinatura HMAC da Meta, descarta duplicatas
(idempotência) e classifica a intenção com Claude Haiku. Mensagens de **voz** são baixadas da
Graph API e transcritas com Whisper antes de seguir pelo mesmo caminho de uma mensagem
digitada — os modelos Claude não aceitam áudio como entrada, daí o segundo fornecedor.

Entende jargão de período ("essa semana", "quinzena", "mês que vem"), tipo de pagamento e
status. Teto de 15 mensagens por remetente a cada 5 minutos — cada mensagem, de texto **ou**
áudio, custa exatamente 1 token do mesmo bucket, porque ambas disparam chamadas pagas de IA.

### Lembretes automáticos
`NotificacaoWhatsAppScheduler` roda a cada minuto e dispara os lembretes de vencimento no
horário que cada usuário configurou.

### Relatórios
Consulta consolidada e exportação em `.xlsx` (Apache POI) e PDF.

### LGPD
Endpoints de acesso, correção e exclusão de dados pessoais. A exclusão é processada por um job
diário (`SchedulerService`, 03:00).

### Multi-tenant
Toda operação é isolada por empresa. O `empresaId` vem do JWT, é colocado no `TenantContext`
pelo `JwtAuthFilter` e conferido em cada serviço — nenhuma query confia em ID vindo do cliente.
Perfis: `MASTER` (gestão de empresas), `ADMIN` e `OPERADOR`.

---

## Estrutura

```
backend/src/main/java/com/agenda/
├── domain/           # um pacote por domínio (controller + service + entidade + DTOs)
│   ├── auth/         # login, refresh token, logout
│   ├── boleto/  pix/  cheque/     # os três tipos de pagamento
│   ├── arquivo/      # upload/download no R2
│   ├── notificacao/  # preferências + scheduler dos lembretes
│   ├── whatsappagent/# webhook, classificador de intenção, transcrição de áudio
│   ├── relatorio/  lgpd/  empresa/  loja/  usuario/  banco/  coluna/  webhook/
├── security/         # JWT, filtros, rate limiter, blacklist de token
├── whatsapp/         # cliente da Cloud API + download de mídia
├── r2/               # configuração do client S3 apontando pro Cloudflare R2
├── job/              # jobs agendados
├── auditoria/        # trilha de auditoria das escritas
└── shared/           # TenantContext, UserContext, exceções

frontend/src/
├── pages/            # Login, SelecionarLoja, Agenda, Relatórios, Configurações, Empresas
├── components/agenda/# tabelas, cards, modais, leitor de código de barras
├── api/  hooks/  store/  types/  utils/
```

---

## Rodando local

**Pré-requisitos:** Java 21, Maven, Node 18+, Docker (para o Postgres).

### 1. Variáveis de ambiente

Crie um `.env` na raiz com as variáveis da [tabela abaixo](#variáveis-de-ambiente).
Ele está no `.gitignore` e nunca é commitado.

Para rodar o básico (login, agenda, paginação), só o banco e o `JWT_SECRET` precisam ser reais
— as chaves de integração podem ficar com valor de teste.

### 2. Banco

```bash
docker compose up db
```

O Flyway aplica as migrations (`V1` a `V16`) sozinho na subida do backend.
`ddl-auto` é `validate`: o schema **nunca** é alterado pelo Hibernate, só por migration.

### 3. Backend

```bash
cd backend
mvn spring-boot:run     # sobe em http://localhost:8080, profile "local"
```

### 4. Frontend

```bash
cd frontend
npm install
npm run dev             # http://localhost:5173
```

---

## Variáveis de ambiente

| Variável | Obrigatória | Para quê |
|---|---|---|
| `DATABASE_URL` / `DATABASE_USER` / `DATABASE_PASS` | sim | Postgres |
| `DB_POOL_SIZE` | não (5) | tamanho do pool Hikari |
| `JWT_SECRET` | sim | assinatura dos tokens |
| `JWT_EXPIRATION` / `JWT_REFRESH_EXPIRATION` | não | validade dos tokens |
| `CORS_ALLOWED_ORIGINS` | não | origens liberadas (vazio = localhost) |
| `R2_ACCOUNT_ID` / `R2_ACCESS_KEY` / `R2_SECRET_KEY` / `R2_BUCKET` | anexos | Cloudflare R2 |
| `WHATSAPP_PHONE_NUMBER_ID` / `WHATSAPP_TOKEN` | WhatsApp | envio de mensagens e download de mídia |
| `WHATSAPP_WEBHOOK_VERIFY_TOKEN` / `WHATSAPP_APP_SECRET` | WhatsApp | verificação e assinatura HMAC do webhook |
| `ANTHROPIC_API_KEY` / `ANTHROPIC_MODEL` | agente | classificação de intenção (default: `claude-haiku-4-5`) |
| `OPENAI_API_KEY` / `OPENAI_TRANSCRIPTION_MODEL` | áudio | transcrição de voz (default: `whisper-1`) |
| `SPRING_PROFILES_ACTIVE` / `PORT` | não | perfil e porta |
| `TZ` | **ver limitações** | fuso da JVM — afeta a hora dos lembretes |

---

## Testes

```bash
cd backend && mvn test          # 155 testes
cd frontend && npm run build    # typecheck (tsc -b) + build
```

O backend tem cobertura de serviço e controller nos caminhos que importam: isolamento
multi-tenant, agente de WhatsApp, validação de upload, JWT, scheduler. O frontend **não tem
suíte de testes** — a verificação é o typecheck e o build.

---

## Segurança

**Isolamento multi-tenant.** Toda operação compara a empresa do recurso com a do
`TenantContext` e lança 403 se divergir. A chave de arquivo no R2 nunca vem do cliente — sempre
sai do banco, o que fecha o buraco clássico de assinar URL para objeto alheio.

**Upload.** O tipo do arquivo é decidido pelos **bytes** (assinatura do formato), nunca pelo
`Content-Type` que o cliente declara — senão um SVG com script passaria como imagem e o R2 o
devolveria executável. Só JPEG, PNG, WEBP e PDF, até 10 MB. A extensão da key é derivada do
tipo detectado, não do nome do arquivo. URLs de download são assinadas e valem 15 minutos.

**Rate limiting.** Login (por e-mail), webhook do WhatsApp (por telefone) e upload
(60 por empresa a cada 10 min). Sempre por **identidade**, nunca por IP: identidade não é
falsificável como um `X-Forwarded-For`, e um IP mal extraído atrás de proxy colapsaria todos os
usuários num bucket só.

**Auth.** JWT com refresh token e blacklist de logout.

---

## Deploy

Railway, via `Dockerfile` (`backend/railway.json`). O webhook do WhatsApp é exposto por nginx.
Push na `master` dispara o deploy.

As variáveis de ambiente são cadastradas no painel do Railway — o `.env` é só local e não sobe.

---

## Limitações conhecidas

**Fuso horário dos lembretes.** O `NotificacaoWhatsAppScheduler` usa o fuso padrão da JVM. No
Railway ele não é o do Brasil, então um lembrete marcado para "09:00" pode sair em hora errada.
A correção é setar `TZ=America/Sao_Paulo` no ambiente.

**Rate limiter em memória.** Os buckets vivem na memória da instância: com mais de uma
instância, o teto real é multiplicado, e um restart zera as contagens. Serve para cortar abuso,
mas não é uma quota contábil.

**Câmera exige HTTPS.** O leitor de código de barras não funciona apontando o celular para o
servidor local (HTTP) — `getUserMedia` só roda em contexto seguro. Testes de câmera precisam do
ambiente publicado.

**Dependência não usada.** `@ericblade/quagga2` está no `package.json` mas nenhum código a
importa — o leitor usa ZXing. Provável resíduo de uma implementação anterior.
