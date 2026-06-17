package com.lexcv.repositories;

import com.lexcv.models.Pagamento;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PagamentoRepository extends JpaRepository<Pagamento, Integer> {
    List<Pagamento> findByHonorarioId(Integer honorarioId);
}
