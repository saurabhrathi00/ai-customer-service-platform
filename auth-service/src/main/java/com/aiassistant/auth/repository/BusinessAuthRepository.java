package com.aiassistant.auth.repository;

import com.aiassistant.auth.models.dao.BusinessAuthEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BusinessAuthRepository extends JpaRepository<BusinessAuthEntity, String> {
    Optional<BusinessAuthEntity> findByEmail(String email);
}
