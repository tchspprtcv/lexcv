package com.lexcv.repositories;

import com.lexcv.models.Movimentacao;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface MovimentacaoRepository extends JpaRepository<Movimentacao, Integer> {
    List<Movimentacao> findByProcessoId(UUID processoId);
}
