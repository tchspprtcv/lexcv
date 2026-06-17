package com.lexcv.repositories;

import com.lexcv.models.Honorario;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface HonorarioRepository extends JpaRepository<Honorario, Integer> {
    List<Honorario> findByProcessoId(UUID processoId);
}
