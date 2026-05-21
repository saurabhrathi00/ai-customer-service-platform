package com.aiassistant.userbusiness.repository;

import com.aiassistant.userbusiness.models.dao.BusinessEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BusinessRepository extends JpaRepository<BusinessEntity, String> {

    Optional<BusinessEntity> findByEmail(String email);

    boolean existsByEmail(String email);
}