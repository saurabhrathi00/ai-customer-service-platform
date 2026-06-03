package com.aiassistant.userbusiness.repository;

import com.aiassistant.userbusiness.models.dao.BusinessEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BusinessRepository extends JpaRepository<BusinessEntity, String> {

    Optional<BusinessEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BusinessEntity b WHERE b.id = :id")
    Optional<BusinessEntity> findByIdForUpdate(@Param("id") String id);

    @Modifying
    @Query("UPDATE BusinessEntity b SET b.liveDemoSecondsRemaining = b.liveDemoSecondsRemaining - :seconds, b.updatedAt = CURRENT_TIMESTAMP WHERE b.id = :id AND b.liveDemoSecondsRemaining >= :seconds")
    int decrementDemoTime(@Param("id") String id, @Param("seconds") int seconds);
}