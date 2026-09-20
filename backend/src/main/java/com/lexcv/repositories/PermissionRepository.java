package com.lexcv.repositories;

import com.lexcv.models.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PermissionRepository extends JpaRepository<Permission, Integer> {
    Optional<Permission> findByNome(String nome);

    // Phase 124 (CATL-03): exclusao ao nivel de SQL das permissoes reservadas a plataforma --
    // AdminController.getRbac() usa este metodo, nunca findAll(), para construir
    // systemPermissions. Uma linha com reservada_plataforma = TRUE nunca chega ao Java.
    List<Permission> findAllByReservadaPlataformaFalse();
}
