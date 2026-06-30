package com.lexcv.repositories;

import com.lexcv.models.ParecerVersao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParecerVersaoRepository extends JpaRepository<ParecerVersao, UUID> {
    List<ParecerVersao> findBySolicitacaoId(UUID solicitacaoId);

    @Query("SELECT MAX(v.numeroVersao) FROM ParecerVersao v WHERE v.solicitacaoId = :solicitacaoId")
    Optional<Integer> findMaxNumeroVersaoBySolicitacaoId(@Param("solicitacaoId") UUID solicitacaoId);
}
