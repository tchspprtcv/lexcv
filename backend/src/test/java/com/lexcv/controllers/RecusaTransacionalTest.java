package com.lexcv.controllers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 128 (Decisao 3, 128-CONTEXT.md), Plan 02: prova que {@link RecusaTransacional#recusar}
 * de facto flipa o commit de uma transacao real para rollback-only, sob um
 * {@link TransactionInterceptor} verdadeiro -- nunca um mock que apenas verifica que o metodo
 * foi chamado. Um mecanismo que parece certo mas nunca e honrado deixaria todas as recusas dos
 * Planos 03/04 fazer commit silenciosamente de mutacoes parciais.
 */
@ExtendWith(MockitoExtension.class)
class RecusaTransacionalTest {

    @Mock
    private PlatformTransactionManager txManager;

    /**
     * Bean de teste minimo com dois metodos {@code @Transactional} publicos -- {@code recusa()}
     * devolve via {@link RecusaTransacional#recusar}, {@code aceita()} devolve normalmente (200).
     * Classe concreta, sem interface: o proxy CGLIB ({@code setProxyTargetClass(true)}) nao
     * precisa de uma.
     */
    static class BeanTransacionalDeTeste {

        @Transactional
        public ResponseEntity<String> recusa() {
            return RecusaTransacional.recusar(ResponseEntity.status(409).build());
        }

        @Transactional
        public ResponseEntity<String> aceita() {
            return ResponseEntity.ok("ok");
        }
    }

    private BeanTransacionalDeTeste criarProxyComTransacaoReal() {
        ProxyFactory factory = new ProxyFactory(new BeanTransacionalDeTeste());
        factory.setProxyTargetClass(true);
        TransactionInterceptor interceptor = new TransactionInterceptor(
                (TransactionManager) txManager, new AnnotationTransactionAttributeSource());
        factory.addAdvice(interceptor);
        return (BeanTransacionalDeTeste) factory.getProxy();
    }

    @Test
    void recusar_marcaAStatusPassadaAoCommitComoRollbackOnly() {
        SimpleTransactionStatus status = new SimpleTransactionStatus();
        when(txManager.getTransaction(any())).thenReturn(status);
        BeanTransacionalDeTeste proxy = criarProxyComTransacaoReal();

        ResponseEntity<String> resposta = proxy.recusa();

        assertEquals(409, resposta.getStatusCode().value());
        verify(txManager).commit(status);
        assertTrue(status.isRollbackOnly());
    }

    @Test
    void metodoQueRetornaNormalmenteSemChamarRecusar_comitaComRollbackOnlyFalso() {
        SimpleTransactionStatus status = new SimpleTransactionStatus();
        when(txManager.getTransaction(any())).thenReturn(status);
        BeanTransacionalDeTeste proxy = criarProxyComTransacaoReal();

        ResponseEntity<String> resposta = proxy.aceita();

        assertEquals(200, resposta.getStatusCode().value());
        verify(txManager).commit(status);
        assertFalse(status.isRollbackOnly());
    }

    @Test
    void recusar_chamadoDiretamenteSemProxy_naoLancaEDevolveAMesmaInstancia() {
        ResponseEntity<String> original = ResponseEntity.status(409).build();

        ResponseEntity<String> resultado = assertDoesNotThrow(() -> RecusaTransacional.recusar(original));

        assertSame(original, resultado);
    }
}
