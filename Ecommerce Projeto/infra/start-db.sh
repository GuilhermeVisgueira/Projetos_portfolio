#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

NETWORK_NAME="ecommerce-net"
MONGO_CONTAINER="ecommerce-mongodb"
MONGO_VOLUME="ecommerce_mongodb_data"
MONGO_IMAGE="docker.io/library/mongo:7.0"

API_CONTAINER="ecommerce-api"
API_MAVEN_VOLUME="ecommerce_maven_repo"
API_IMAGE="docker.io/library/eclipse-temurin:21-jdk-alpine"

# 0. Garantir rede isolada no Podman para comunicação entre MongoDB e API Java
if ! podman network exists "$NETWORK_NAME"; then
  echo "[0/5] Criando rede Podman '$NETWORK_NAME'..."
  podman network create "$NETWORK_NAME"
fi

# 1. Garantir volumes persistentes (MongoDB + cache Maven)
if ! podman volume exists "$MONGO_VOLUME"; then
  echo "[1/5] Criando volume '$MONGO_VOLUME'..."
  podman volume create "$MONGO_VOLUME"
fi
if ! podman volume exists "$API_MAVEN_VOLUME"; then
  podman volume create "$API_MAVEN_VOLUME" >/dev/null
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

# 4. Popular/Atualizar banco 'ecommerce' (apenas se ainda estiver vazio)
echo "[4/5] Verificando estrutura e dados do banco 'ecommerce'..."
PRODUCTS_COUNT=$(podman exec "$MONGO_CONTAINER" mongosh --quiet ecommerce --eval "db.products.countDocuments()" 2>/dev/null || echo "0")
if [ "$PRODUCTS_COUNT" = "0" ]; then
  podman exec -i "$MONGO_CONTAINER" mongosh --quiet < "$SCRIPT_DIR/mongo-init/seed-ecommerce.js"
else
  echo "Banco 'ecommerce' já populado ($PRODUCTS_COUNT produtos ativos)."
fi

# 5. Criar ou atualizar o container da API Java (Spring Boot) conectado ao MongoDB
mkdir -p "$PROJECT_DIR/backend" "$PROJECT_DIR/tests"
echo "[5/5] Subindo container '$API_CONTAINER' (Spring Boot + Java 21) na porta 8080..."
podman rm -f "$API_CONTAINER" >/dev/null 2>&1 || true
podman run -d \
  --name "$API_CONTAINER" \
  --network host \
  -w /app \
  -e SPRING_DATA_MONGODB_URI="mongodb://localhost:27017/ecommerce?replicaSet=rs0&directConnection=true" \
  -e SERVER_PORT="8080" \
  -v "$PROJECT_DIR/backend:/app:Z" \
  -v "$PROJECT_DIR/tests:/tests:Z" \
  -v "$API_MAVEN_VOLUME:/root/.m2:Z" \
  "$API_IMAGE" \
  sh -c "apk add --no-cache maven >/dev/null 2>&1 || true; if [ -f ./pom.xml ]; then mvn spring-boot:run; else echo 'Ambiente Java 21 (ecommerce-api) pronto...'; tail -f /dev/null; fi"

echo "Aguardando API Spring Boot iniciar na porta 8080..."
for i in {1..30}; do
  if curl -sf http://localhost:8080/api/v1/status >/dev/null 2>&1; then
    echo "API conectada ao MongoDB com sucesso:"
    curl -s http://localhost:8080/api/v1/status
    echo ""
    break
  fi
  sleep 1
done

echo "Ambientes Podman prontos:"
echo "  - MongoDB: mongodb://localhost:27017/ecommerce?replicaSet=rs0"
echo "  - API Spring Boot: http://localhost:8080/api/v1/status"
