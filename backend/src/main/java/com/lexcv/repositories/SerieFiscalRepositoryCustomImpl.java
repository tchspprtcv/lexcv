package com.lexcv.repositories;

import com.lexcv.models.SerieFiscal;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/** Implementação do fragmento {@link SerieFiscalRepositoryCustom} (detetada pelo Spring Data). */
class SerieFiscalRepositoryCustomImpl implements SerieFiscalRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public void refrescar(SerieFiscal serie) {
        entityManager.refresh(serie);
    }
}
