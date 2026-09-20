package com.lexcv.repositories;

import com.lexcv.models.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Integer> {
    Optional<Role> findByNome(String nome);

    // Phase 125 (MOLD-01): unica porta de leitura de moldes -- exclusao de papeis
    // nao-instanciaveis (incluindo PLATAFORMA_ADMIN) fechada ao nivel de SQL, nunca por filtro
    // Java pos-leitura (mesmo padrao que PermissionRepository.findAllByReservadaPlataformaFalse(), Phase 124).
    List<Role> findAllByInstanciavelTrue();
}
