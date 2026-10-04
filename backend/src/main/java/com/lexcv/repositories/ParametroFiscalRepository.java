package com.lexcv.repositories;

import com.lexcv.models.ParametroFiscal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 133 (CFG-04): parâmetros fiscais com vigência. Intencionalmente GLOBAL (sem tenantId):
 * regra legal comum a todos os escritórios (decisão do CONTEXT) -- a única exceção à regra
 * "todo o finder recebe tenantId". Não existe endpoint de escrita no v3.0; só o seeder escreve.
 */
@Repository
public interface ParametroFiscalRepository extends JpaRepository<ParametroFiscal, UUID> {

    Optional<ParametroFiscal> findByCodigoAndVigenteDesde(String codigo, LocalDate vigenteDesde);

    /** Valor vigente numa data: a linha mais recente com vigente_desde <= data. */
    Optional<ParametroFiscal> findFirstByCodigoAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(
            String codigo, LocalDate data);
}
