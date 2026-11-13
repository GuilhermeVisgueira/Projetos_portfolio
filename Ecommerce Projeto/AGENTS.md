# AGENTS.md — Contexto para IA (E-commerce)

> **Regra de Dupla Documentação (OBRIGATÓRIO):**
> 1. **Para a IA (este arquivo `AGENTS.md`)**: Salvo na raiz do projeto (`/home/gvisgueira/Área de trabalho/Projetos_portfolio/Ecommerce Projeto/AGENTS.md`). Deve ser **resumido, direto e técnico** para leitura rápida de contexto pela IA a cada sessão. Sempre atualize este arquivo ao avançar etapas.
> 2. **Para o Usuário (Obsidian)**: Salvo em `/home/gvisgueira/Área de trabalho/guilfedora/Ecommerce/`. Deve conter **documentação explicativa e didática** (sem exagero) sobre as tecnologias utilizadas, decisões de arquitetura e funcionamento. A pasta do Obsidian é exclusiva para notas `.md` (não colocar código/infra lá).

---

## 1. Localização e Git
- **Repositório Git Local**: `/home/gvisgueira/Área de trabalho/Projetos_portfolio/`
- **Diretório deste Projeto**: `/home/gvisgueira/Área de trabalho/Projetos_portfolio/Ecommerce Projeto/`
- **Vault Obsidian (Docs do Usuário)**: `/home/gvisgueira/Área de trabalho/guilfedora/Ecommerce/`

## 2. Stack Tecnológica
- **Containers & Rede (Podman rootless)**:
  - Rede isolada: `ecommerce-net` (bridge).
  - Container Banco: `ecommerce-mongodb` (`mongo:7.0`, porta `27017`, Replica Set `rs0` ativo para transações ACID `@Transactional`).
    - URI externa (host/Compass): `mongodb://localhost:27017/ecommerce?replicaSet=rs0`
    - URI interna (container `ecommerce-api`): `mongodb://ecommerce-mongodb:27017/ecommerce?replicaSet=rs0`
  - Container API: `ecommerce-api` (`eclipse-temurin:21-jdk-alpine`, porta `8080`, volume `./backend:/app:Z`).
- **Back-end**: Java 21 + Spring Boot 3.x (Spring Data MongoDB, `MongoTransactionManager`, Spring Security + JWT/BCrypt, Stripe Java SDK, `JavaMailSender`).
- **Front-end**: C# (.NET 8) com **Blazor** consumindo a API REST do Spring Boot (`http://localhost:8080/api/v1/...`).

## 3. Estrutura do Projeto
```text
Ecommerce Projeto/
├── AGENTS.md                  # Contexto enxuto para a IA
├── infra/                     # Configuração Podman (MongoDB + Container API Java)
│   ├── podman-compose.yml     # Definição declarativa dos serviços e rede ecommerce-net
│   ├── start-db.sh            # Script para subir rede, MongoDB rs0, popular banco e subir container ecommerce-api
│   └── mongo-init/
│       ├── init-replica.js    # Inicializa o Replica Set rs0
│       └── seed-ecommerce.js  # Cria coleções, índices (email, cpf, sku) e dados variados
├── backend/                   # Containerfile pronto; código da API Java Spring Boot pendente
│   └── Containerfile
└── frontend/                  # (Em breve) Aplicação Web em C# Blazor (.NET 8)
```

## 3.1. Modelagem do Banco (`ecommerce`)
- **`users`**: `_id`, `name`, `email` (unique index), `cpf` (unique index), `birthDate` (Date), `phone`, `address` (`street`, `number`, `complement`, `neighborhood`, `city`, `state`, `zipCode`, `country`), `passwordHash` (BCrypt), `role` (`CUSTOMER`|`ADMIN`), `createdAt`.
- **`products`**: `_id`, `sku` (unique index), `name`, `description`, `priceCents` (int, centavos p/ Stripe), `currency` (`BRL`), `stock` (int), `category`, `attributes` (`Map<String, Object>` flexível/polimórfico para itens variados: eletrônicos, vestuário, livros, alimentos, etc.), `active`, `createdAt`.
- **`orders`**: `_id`, `userId` (index), `status` (`PENDING`|`PAID`|`CANCELED`|`FAILED`), `items` (snapshot), `totalAmountCents`, `currency`, `stripeSessionId` (sparse index), `shippingAddress`, `createdAt`, `paidAt`.

## 4. Comandos Rápidos (Infra / Podman)
| Ação | Comando |
| :--- | :--- |
| Subir / Atualizar ambientes Podman (`mongodb` + `api`) | `bash infra/start-db.sh` |
| Verificar containers rodando | `podman ps` |
| Verificar status do Replica Set | `podman exec ecommerce-mongodb mongosh --quiet --eval "rs.status().ok"` |
| Parar containers | `podman stop ecommerce-api ecommerce-mongodb` |

## 5. Status Atual do Desenvolvimento
- [x] Documentação no Obsidian (`arquitetura-fluxos.md`, `bancos-de-dados-e-transacoes.md`, `infra-podman-e-mongodb.md`, `backend-java-models-e-rotas.md`).
- [x] Containers `ecommerce-mongodb` (`rs0`) e `ecommerce-api` (Java 21) ativos no Podman na rede `ecommerce-net`.
- [x] Banco `ecommerce` populado com produtos variados (`attributes` dinâmicos) e usuários completos (`cpf`, `birthDate`, `phone`, `address`).
- [ ] Implementar código da API Back-end (Java Spring Boot).
- [ ] Inicializar projeto Front-end em C# (**Blazor**).
