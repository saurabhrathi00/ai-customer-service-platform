package com.aiassistant.userbusiness.repository;

import com.aiassistant.userbusiness.models.dao.RatingConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RatingConfigRepository extends JpaRepository<RatingConfigEntity, String> {

    List<RatingConfigEntity> findAllByBusinessId(String businessId);

    Optional<RatingConfigEntity> findByBusinessIdAndSignalKey(String businessId, String signalKey);
}