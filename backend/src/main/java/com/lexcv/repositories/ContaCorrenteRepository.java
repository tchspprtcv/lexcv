package com.lexcv.repositories;

import com.lexcv.models.ContaCorrente;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ContaCorrenteRepository extends JpaRepository<ContaCorrente, Integer> {
    Optional<ContaCorrente> findByClienteId(UUID clienteId);
}
