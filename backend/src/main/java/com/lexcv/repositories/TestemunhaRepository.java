package com.lexcv.repositories;

import com.lexcv.models.Testemunha;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface TestemunhaRepository extends JpaRepository<Testemunha, Integer> {
    List<Testemunha> findByProcessoId(UUID processoId);
}
