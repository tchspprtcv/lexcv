package com.lexcv.repositories;

import com.lexcv.models.SystemSetting;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SystemSetting s where s.id = :id")
    Optional<SystemSetting> findByIdForUpdate(@Param("id") Long id);
}
