// Script de criação e atualização do banco 'ecommerce'
// Inclui: coleções, validação de schema base, índices únicos e dados variados de exemplo
const dbEcommerce = db.getSiblingDB("ecommerce");

const existingCollections = dbEcommerce.getCollectionNames();

if (!existingCollections.includes("users")) {
  dbEcommerce.createCollection("users");
}
if (!existingCollections.includes("products")) {
  dbEcommerce.createCollection("products");
}
if (!existingCollections.includes("orders")) {
  dbEcommerce.createCollection("orders");
}

// 1. Índices da coleção 'users' (email e cpf únicos)
dbEcommerce.users.createIndex({ email: 1 }, { unique: true, name: "idx_users_email_unique" });
dbEcommerce.users.createIndex({ cpf: 1 }, { unique: true, name: "idx_users_cpf_unique" });

// 2. Índices da coleção 'products' (sku único, busca por categoria/ativo)
dbEcommerce.products.createIndex({ sku: 1 }, { unique: true, name: "idx_products_sku_unique" });
dbEcommerce.products.createIndex({ category: 1, active: 1 }, { name: "idx_products_category_active" });

// 3. Índices da coleção 'orders'
dbEcommerce.orders.createIndex({ userId: 1, createdAt: -1 }, { name: "idx_orders_user_date" });
dbEcommerce.orders.createIndex({ stripeSessionId: 1 }, { sparse: true, name: "idx_orders_stripe_session" });

// 4. Atualizar catálogo de produtos variados (demonstração da flexibilidade do MongoDB)
dbEcommerce.products.deleteMany({});
dbEcommerce.products.insertMany([
  {
    sku: "TECH-NB-001",
    name: "Notebook Pro Dev 16\"",
    description: "Notebook de alta performance para desenvolvimento e containers.",
    priceCents: 749900, // R$ 7.499,00
    currency: "BRL",
    stock: 15,
    category: "Eletrônicos",
    // Atributos específicos de Hardware
    attributes: {
      brand: "TechPro",
      ram: "32GB DDR5",
      storage: "1TB NVMe Gen4",
      processor: "Ryzen 9",
      voltage: "Bivolt",
      warrantyMonths: 12
    },
    active: true,
    createdAt: new Date()
  },
  {
    sku: "MODA-CAM-002",
    name: "Camiseta Oversized Algodão Egípcio",
    description: "Camiseta casual de alta durabilidade e toque macio.",
    priceCents: 12990, // R$ 129,90
    currency: "BRL",
    stock: 60,
    category: "Vestuário",
    // Atributos específicos de Moda/Roupas
    attributes: {
      size: "GG",
      color: "Preto Ônix",
      material: "93% Algodão Egípcio, 7% Elastano",
      gender: "Unissex",
      washingInstructions: "Lavar à mão, não usar secadora"
    },
    active: true,
    createdAt: new Date()
  },
  {
    sku: "LIV-ARQ-003",
    name: "Arquitetura Limpa e Sistemas Distribuídos",
    description: "Guia prático de engenharia de software moderna com Java e C#.",
    priceCents: 8990, // R$ 89,90
    currency: "BRL",
    stock: 35,
    category: "Livros",
    // Atributos específicos de Livros
    attributes: {
      isbn: "978-85-00000-01-2",
      author: "Roberto Silva",
      publisher: "Editora DevBooks",
      pages: 412,
      coverType: "Capa Dura",
      language: "Português"
    },
    active: true,
    createdAt: new Date()
  },
  {
    sku: "CAFE-ESP-004",
    name: "Café Especial em Grãos Serra da Mantiqueira 500g",
    description: "Café 100% Arábica com notas de chocolate amargo e caramelo.",
    priceCents: 6450, // R$ 64,50
    currency: "BRL",
    stock: 25,
    category: "Alimentos e Bebidas",
    // Atributos específicos de Alimentos
    attributes: {
      roastLevel: "Média",
      weightGrams: 500,
      origin: "Mantiqueira de Minas - MG",
      altitudeMeters: 1250,
      sensoryNotes: ["Chocolate Amargo", "Caramelo", "Avelã"],
      expirationDate: new Date("2027-06-01T00:00:00Z")
    },
    active: true,
    createdAt: new Date()
  }
]);
print("Catálogo com produtos variados atualizado na coleção 'products'.");

// 5. Inserir usuários iniciais completos (nome, id, email, data de nascimento, endereço, telefone, cpf)
dbEcommerce.users.deleteMany({});
dbEcommerce.users.insertMany([
  {
    name: "Guilherme Visgueira",
    email: "guilherme@exemplo.com",
    cpf: "123.456.789-00",
    birthDate: new Date("2000-05-15T00:00:00Z"),
    phone: "+55 11 98765-4321",
    address: {
      street: "Avenida Paulista",
      number: "1000",
      complement: "Apto 42",
      neighborhood: "Bela Vista",
      city: "São Paulo",
      state: "SP",
      zipCode: "01310-100",
      country: "Brasil"
    },
    passwordHash: "$2a$10$ExemploDeHashBCryptParaDesenvolvimento1234567890",
    role: "ADMIN",
    createdAt: new Date()
  },
  {
    name: "Maria Oliveira",
    email: "maria.oliveira@exemplo.com",
    cpf: "987.654.321-11",
    birthDate: new Date("1996-10-22T00:00:00Z"),
    phone: "+55 21 99887-6655",
    address: {
      street: "Rua Visconde de Pirajá",
      number: "450",
      complement: "Bloco B, Sala 201",
      neighborhood: "Ipanema",
      city: "Rio de Janeiro",
      state: "RJ",
      zipCode: "22410-002",
      country: "Brasil"
    },
    passwordHash: "$2a$10$ExemploDeHashBCryptParaClienteMaria9876543210",
    role: "CUSTOMER",
    createdAt: new Date()
  }
]);
print("Usuários completos (com CPF, telefone, data de nascimento e endereço) inseridos na coleção 'users'.");
print("Banco de dados 'ecommerce' atualizado com sucesso!");
