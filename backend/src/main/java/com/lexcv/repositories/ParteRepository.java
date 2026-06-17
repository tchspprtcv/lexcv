package com.lexcv.repositories;

import com.lexcv.models.Parte;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ParteRepository extends JpaRepository<Parte, Integer> {
    List<Parte> findByProcessoId(UUID processoId);
}
