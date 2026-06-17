package com.lexcv.repositories;

import com.lexcv.models.FaseProcessual;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface FaseProcessualRepository extends JpaRepository<FaseProcessual, Integer> {
    Optional<FaseProcessual> findByNome(String nome);
}
