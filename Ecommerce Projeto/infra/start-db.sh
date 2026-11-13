#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

NETWORK_NAME="ecommerce-net"
MONGO_CONTAINER="ecommerce-mongodb"
MONGO_VOLUME="ecommerce_mongodb_data"
MONGO_IMAGE="docker.io/library/mongo:7.0"

API_CONTAINER="ecommerce-api"
API_IMAGE="docker.io/library/eclipse-temurin:21-jdk-alpine"

# 0. Garantir rede isolada no Podman para comunicação entre MongoDB e API Java
if ! podman network exists "$NETWORK_NAME"; then
  echo "[0/5] Criando rede Podman '$NETWORK_NAME'..."
  podman network create "$NETWORK_NAME"
fi

# 1. Garantir volume persistente do MongoDB
if ! podman volume exists "$MONGO_VOLUME"; then
  echo "[1/5] Criando volume '$MONGO_VOLUME'..."
  podman volume create "$MONGO_VOLUME"
fi

# 2. Criar ou iniciar o container do MongoDB (conectado na rede ecommerce-net)
if podman container exists "$MONGO_CONTAINER"; then
  echo "[2/5] Garantindo que '$MONGO_CONTAINER' está em execução e na rede '$NETWORK_NAME'..."
  podman start "$MONGO_CONTAINER" >/dev/null
  podman network connect "$NETWORK_NAME" "$MONGO_CONTAINER" 2>/dev/null || true
else
  echo "[2/5] Subindo container '$MONGO_CONTAINER' com Replica Set rs0..."
  podman run -d \
    --name "$MONGO_CONTAINER" \
    --network "$NETWORK_NAME" \
    -p 27017:27017 \
    -v "$MONGO_VOLUME:/data/db:Z" \
    "$MONGO_IMAGE" \
    mongod --replSet rs0 --bind_ip_all
fi

# 3. Aguardar mongod responder e inicializar o Replica Set rs0
echo "[3/5] Aguardando MongoDB iniciar para configurar Replica Set rs0..."
for i in {1..30}; do
  if podman exec "$MONGO_CONTAINER" mongosh --quiet --eval "db.runCommand({ ping: 1 }).ok" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

podman exec "$MONGO_CONTAINER" mongosh --quiet --eval '
try {
  const status = rs.status();
  print("Replica Set rs0 já está ativo (ok=" + status.ok + ").");
} catch (e) {
  print("Inicializando Replica Set rs0...");
  rs.initiate({ _id: "rs0", members: [{ _id: 0, host: "localhost:27017" }] });
}
'

for i in {1..15}; do
  IS_PRIMARY=$(podman exec "$MONGO_CONTAINER" mongosh --quiet --eval "db.hello().isWritablePrimary" 2>/dev/null || echo "false")
  if [ "$IS_PRIMARY" = "true" ]; then
    break
  fi
  sleep 1
done

# 4. Popular/Atualizar banco 'ecommerce'
echo "[4/5] Garantindo estrutura e dados do banco 'ecommerce'..."
podman exec -i "$MONGO_CONTAINER" mongosh --quiet < "$SCRIPT_DIR/mongo-init/seed-ecommerce.js"

# 5. Criar ou iniciar o container de ambiente da API Java (sem criar o código da API ainda)
mkdir -p "$PROJECT_DIR/backend"
if podman container exists "$API_CONTAINER"; then
  echo "[5/5] Garantindo que o container da API ('$API_CONTAINER') está em execução..."
  podman start "$API_CONTAINER" >/dev/null
else
  echo "[5/5] Criando e iniciando container '$API_CONTAINER' (Java 21) na porta 8080..."
  podman run -d \
    --name "$API_CONTAINER" \
    --network "$NETWORK_NAME" \
    -p 8080:8080 \
    -w /app \
    -e SPRING_DATA_MONGODB_URI="mongodb://ecommerce-mongodb:27017/ecommerce?replicaSet=rs0" \
    -e SERVER_PORT="8080" \
    -v "$PROJECT_DIR/backend:/app:Z" \
    "$API_IMAGE" \
    sh -c "if [ -f ./mvnw ]; then ./mvnw spring-boot:run; else echo 'Ambiente Java 21 (ecommerce-api) pronto aguardando criação das APIs...'; tail -f /dev/null; fi"
fi

echo "Ambientes Podman prontos:"
echo "  - MongoDB: mongodb://localhost:27017/ecommerce?replicaSet=rs0"
echo "  - API Container (Java 21):ecommerce-api rodando na porta 8080"
