package com.lexcv.repositories;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.spi.BootstrapContext;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.jpa.boot.spi.IntegratorProvider;
import org.hibernate.service.spi.SessionFactoryServiceRegistry;

import java.util.List;

/**
 * Só para testes: guarda o {@link Metadata} do arranque do Hibernate, para que um IT possa correr
 * o {@code SchemaMigrator} (o mesmo de {@code ddl-auto=update}) contra o esquema já criado e ver
 * que DDL um segundo arranque emitiria. Ligado com
 * {@code spring.jpa.properties.hibernate.integrator_provider} (ver {@code MigracaoFiscal133IT}).
 */
public class CapturaMetadataHibernate implements IntegratorProvider {

    private static volatile Metadata metadata;
    private static volatile SessionFactoryImplementor sessionFactory;

    @Override
    public List<Integrator> getIntegrators() {
        return List.of(new Integrator() {
            @Override
            public void integrate(Metadata m, BootstrapContext bootstrapContext, SessionFactoryImplementor sf) {
                metadata = m;
                sessionFactory = sf;
            }

            @Override
            public void disintegrate(SessionFactoryImplementor sf, SessionFactoryServiceRegistry registry) {
                // nada a libertar
            }
        });
    }

    public static Metadata metadata() {
        return metadata;
    }

    public static SessionFactoryImplementor sessionFactory() {
        return sessionFactory;
    }
}
