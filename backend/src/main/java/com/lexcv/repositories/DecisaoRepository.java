package com.lexcv.repositories;

import com.lexcv.models.Decisao;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface DecisaoRepository extends JpaRepository<Decisao, Integer> {
    List<Decisao> findByProcessoId(UUID processoId);
}
