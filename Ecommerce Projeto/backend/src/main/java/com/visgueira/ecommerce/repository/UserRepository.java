package com.visgueira.ecommerce.repository;

import com.visgueira.ecommerce.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByEmail(String email);

    Optional<User> findByCpf(String cpf);

    Optional<User> findByVerificationToken(String verificationToken);

    boolean existsByEmail(String email);

    boolean existsByCpf(String cpf);
}
