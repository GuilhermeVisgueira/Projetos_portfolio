package com.visgueira.ecommerce.controller;

import com.visgueira.ecommerce.repository.ProductRepository;
import com.visgueira.ecommerce.repository.UserRepository;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/status")
public class DatabaseHealthController {

    private final MongoTemplate mongoTemplate;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public DatabaseHealthController(MongoTemplate mongoTemplate,
                                    ProductRepository productRepository,
                                    UserRepository userRepository) {
        this.mongoTemplate = mongoTemplate;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> checkDatabaseConnection() {
        Document pingResult = mongoTemplate.executeCommand(new Document("ping", 1));
        Document helloResult = mongoTemplate.executeCommand(new Document("hello", 1));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("apiStatus", "ONLINE");
        response.put("database", mongoTemplate.getDb().getName());
        response.put("mongodbPingOk", pingResult.get("ok"));
        response.put("replicaSetName", helloResult.getString("setName"));
        response.put("isWritablePrimary", helloResult.getBoolean("isWritablePrimary", false));
        response.put("counts", Map.of(
            "products", productRepository.count(),
            "users", userRepository.count()
        ));
        response.put("timestamp", Instant.now());

        return ResponseEntity.ok(response);
    }
}
