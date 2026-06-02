package com.aiassistant.userbusiness.repository;

import com.aiassistant.userbusiness.models.dao.TelephonyProviderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TelephonyProviderRepository extends JpaRepository<TelephonyProviderEntity, String> {

    Optional<TelephonyProviderEntity> findBySlug(String slug);
}
