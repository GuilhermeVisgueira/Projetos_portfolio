# AGENTS.md — Contexto para IA (E-commerce)

> **Regra de Dupla Documentação (OBRIGATÓRIO):**
> 1. **Para a IA (este arquivo `AGENTS.md`)**: Salvo na raiz do projeto (`/home/gvisgueira/Área de trabalho/Projetos_portfolio/Ecommerce Projeto/AGENTS.md`). Deve ser **resumido, direto e técnico** para leitura rápida de contexto pela IA a cada sessão. Sempre atualize este arquivo ao avançar etapas.
> 2. **Para o Usuário (Obsidian)**: Salvo em `/home/gvisgueira/Área de trabalho/guilfedora/Ecommerce/`. Deve conter **documentação explicativa e didática** (sem exagero) sobre as tecnologias utilizadas, decisões de arquitetura e funcionamento. A pasta do Obsidian é exclusiva para notas `.md` (não colocar código/infra lá).

---

## 1. Localização e Git
- **Repositório Git Local**: `/home/gvisgueira/Área de trabalho/Projetos_portfolio/`
- **Diretório deste Projeto**: `/home/gvisgueira/Área de trabalho/Projetos_portfolio/Ecommerce Projeto/`
- **Vault Obsidian (Docs do Usuário)**: `/home/gvisgueira/Área de trabalho/guilfedora/Ecommerce/`

---
trigger: alwaysOn
description: Padrão obrigatório de mensagens de commit (Conventional Commits com título conciso e descrição multilinear)
---

# 📝 Padrão de Mensagens de Commit e Segurança Git

## 1. Estrutura Padrão do Commit
```text
<tipo>(<escopo>): <título curto no imperativo (máx. 72 caracteres)>

<descrição detalhada explicativa (multilinha, quando necessário)>
<explique o "porquê" e o "o quê" mudou em relação ao estado anterior>
```
- **Segurança Git**: Verificar `git fetch` antes de `pull`, proibido `push --force`/`reset --hard` na `main` sem confirmação explícita, varrer credenciais antes de `git add` e validar compilação/testes antes de `push`.

---

# 🧪 Diretrizes de Engenharia de QA e Arquitetura de Testes (OBRIGATÓRIO)

Sempre que criar uma nova funcionalidade, corrigir um bug ou implementar testes neste repositório, siga estritamente estas regras de arquitetura e qualidade (Pirâmide de Testes: Unitários, Integração e E2E):

## 1. Organização e Estrutura de Diretórios de Teste
- Nunca misture arquivos de teste com o código de produção em `src/main`.
- Todos os testes residem na pasta `tests/` na raiz do projeto (`/home/gvisgueira/Área de trabalho/Projetos_portfolio/Ecommerce Projeto/tests/`), configurada como `<testSourceDirectory>../tests</testSourceDirectory>` no `backend/pom.xml`:
  - `tests/Unit/`: Testes rápidos e isolados de regras de negócio, cálculos de carrinho/frete/cupons, serviços e domínio (usando JUnit 5 + Mockito).
  - `tests/Integration/`: Testes de repositórios, banco de dados (em container `ecommerce-mongodb` isolado com transações `rs0`) e contratos HTTP de endpoints REST/Webhooks (`MockMvc` / `@SpringBootTest`).
  - `tests/E2E/`: Fluxos críticos completos (jornada ponta a ponta: registro $\rightarrow$ verificação de e-mail $\rightarrow$ login JWT $\rightarrow$ carrinho e frete $\rightarrow$ checkout Stripe $\rightarrow$ webhook autenticado e baixa de estoque ACID).
  - `tests/Fixtures/`: Builders, mocks reutilizáveis e geradores de payloads/assinaturas HMAC de teste.

## 2. Definition of Done (O Que Testar em Cada Funcionalidade)
Nenhuma funcionalidade está completa sem seus respectivos testes cobrindo os 5 módulos críticos (*Catálogo/Estoque, Carrinho/Precificação/Frete, Autenticação/Segurança, Checkout/Pagamentos Stripe, Pedidos/Notificações*):
1. **Caminho Feliz (Happy Path):** Fluxo principal com entradas válidas.
2. **Casos de Borda e Falha (Edge Cases):** Limites numéricos (valores zero, negativos, quantidades acima do limite), listas vazias, CEPs inválidos, cupons expirados/inativos, estoque insuficiente e estados inconsistentes.
3. **Segurança e Autorização:** Bloqueio de usuários não autenticados (`401`/`403`), sem e-mail verificado (`403`) ou sem role adequada (`CUSTOMER` tentando criar produto exclusivo de `SELLER`/`ADMIN`), e isolamento de dados entre usuários.
4. **Idempotência (Pagamentos/Pedidos):** Garantia contra processamento duplicado de webhooks `checkout.session.completed` para evitar cobrança/baixa dupla de estoque.

## 3. Padrão de Escrita de Código de Teste
- **Nomenclatura Explícita:** Todos os métodos de teste devem usar o padrão:
  `UnidadeTestada_CenarioOuCondicao_ResultadoEsperado`
  *(Ex: `CalculateShipping_WhenSubtotalReachesFreeShippingThreshold_ShouldReturnZeroFreight`)*
- **Estrutura AAA Obrigatória:** Divida visualmente cada teste com os comentários:
  - `// Arrange`: Preparação de dados, fixtures e mocks.
  - `// Act`: Execução única do método ou requisição testada.
  - `// Assert`: Verificação objetiva do retorno, status HTTP, estado no MongoDB ou interações de mock.
- **Isolamento e Mocks Conscientes:** Testes devem ser independentes da ordem de execução (limpar estado entre testes de integração). Em testes unitários, faça mock de todo I/O externo (MongoDB, `JavaMailSender`, API externa da Stripe), sem chamadas reais à internet.

## 4. Execução e Validação Contínua
- Antes de concluir qualquer tarefa, execute a suíte de testes (`podman exec ecommerce-api mvn test`) garantindo 100% de aprovação sem regressões.
- Nunca desative testes (`@Disabled`) ou remova asserções para forçar aprovação do build.

---

## 2. Stack Tecnológica e Arquitetura Atual
- **Containers (Podman)**:
  - `ecommerce-mongodb` (`mongo:7.0`, porta `27017`, Replica Set `rs0` ativo para `@Transactional`).
  - `ecommerce-api` (`eclipse-temurin:21-jdk-alpine` + Maven, porta `8080`, volume `./backend:/app:Z` e `./tests:/tests:Z`).
- **Back-end**: Java 21 + Spring Boot 3.3.5 (Spring Data MongoDB, `MongoTransactionManager`, Spring Security + JWT/BCrypt, Stripe Java SDK, `JavaMailSender`).
- **Front-end**: C# (.NET 8) com **Blazor**.

## 3. Estrutura do Projeto
```text
Ecommerce Projeto/
├── AGENTS.md                  # Contexto técnico e regras de QA para a IA
├── infra/                     # Configuração Podman (MongoDB rs0 + Container API Java)
├── backend/                   # Código de produção da API Java Spring Boot (src/main)
├── tests/                     # Suíte de Testes (Pirâmide de Testes)
│   ├── Fixtures/              # Builders e geradores de payloads/assinaturas de teste
│   ├── Unit/                  # Testes unitários isolados (Mockito)
│   ├── Integration/           # Testes de integração (MongoDB rs0 + MockMvc HTTP)
│   └── E2E/                   # Fluxos críticos completos (Smoke / Jornada de Compra)
└── frontend/                  # (Em breve) Aplicação Web em C# Blazor (.NET 8)
```

## 3.1. Modelagem do Banco (`ecommerce`)
- **`users`**: `_id`, `name`, `email` (unique), `cpf` (unique), `birthDate`, `phone`, `address`, `passwordHash`, `role` (`CUSTOMER`|`SELLER`|`ADMIN`), `storeName`, `cnpj`, `emailVerified`, `verificationToken`, `verificationTokenExpiresAt`, `createdAt`.
- **`products`**: `_id`, `sku` (unique), `name`, `description`, `priceCents`, `weightGrams` (p/ cálculo de frete), `currency`, `stock`, `category`, `sellerId`, `sellerStoreName`, `attributes` (`Map<String, Object>`), `active`, `createdAt`.
- **`carts`**: `_id`, `userId` (unique index), `items` (`sku`, `name`, `unitPriceCents`, `weightGrams`, `quantity`), `couponCode`, `discountCents`, `zipCode`, `shippingService` (`PAC`|`SEDEX`), `shippingCents`, `estimatedDeliveryDays`, `subtotalCents`, `totalCents`, `updatedAt`.
- **`coupons`**: `_id`, `code` (unique), `discountPercentage`, `maxDiscountCents`, `minOrderCents`, `expiresAt`, `active`.
- **`orders`**: `_id`, `userId`, `customerEmail`, `status` (`PENDING`|`PAID`|`CANCELED`|`FAILED`), `items`, `subtotalCents`, `discountCents`, `shippingCents`, `shippingService`, `couponCode`, `totalAmountCents`, `currency`, `stripeSessionId`, `stripeCheckoutUrl`, `shippingAddress`, `createdAt`, `paidAt`.

## 4. Comandos Rápidos
| Ação | Comando |
| :--- | :--- |
| Subir / Atualizar ambientes Podman (`mongodb` + `api`) | `bash infra/start-db.sh` |
| Executar toda a Pirâmide de Testes (`tests/`) | `podman exec ecommerce-api mvn test` |
| Verificar containers rodando | `podman ps` |
