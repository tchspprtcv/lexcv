package com.lexcv.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

/**
 * Phase 128 (Decisao 3, 128-CONTEXT.md): "um pedido recusado nao grava evento -- nao houve
 * mudanca". Os Planos 03/04 tornam os handlers de escrita RBAC {@code @Transactional} para que o
 * evento de auditoria (Plan 02, {@link com.lexcv.services.AuditoriaRbacService}) e a mudanca que
 * descreve façam commit ou rollback juntos. Isso introduz um problema novo que nao existia antes
 * desses handlers serem transacionais: {@code recusar} e o mecanismo que o fecha.
 *
 * <p>O raciocinio completo, porque nao e obvio:
 * <ul>
 *   <li>Uma vez que um handler passa a ser {@code @Transactional}, devolver um
 *       {@code ResponseEntity} de erro (409, 403, 404, ...) e um retorno NORMAL do metodo do
 *       ponto de vista do Spring -- nao uma excecao. O {@code TransactionInterceptor} faz
 *       COMMIT em qualquer retorno normal, recusa incluida.</li>
 *   <li>Com {@code open-in-view} ligado (default do Spring Boot, nao sobreposto em
 *       {@code application.yml} deste repositorio), uma entidade carregada por
 *       {@code findById} continua gerida durante o pedido inteiro. Qualquer mutacao em memoria
 *       feita ANTES de uma recusa mais tarde no mesmo metodo (por exemplo,
 *       {@code AdminController.updateUser} chama {@code setEmail}/{@code setNome} antes de um
 *       403/409 posterior) seria persistida por dirty-checking nesse commit. Hoje, sem
 *       transacao a volta do handler, esse flush nunca acontece -- e e esse comportamento actual
 *       que {@code recusar} preserva quando os handlers ganham {@code @Transactional}.</li>
 *   <li>Marcar a transacao como rollback-only mantem o comportamento de hoje. Tambem faz uma
 *       {@code DataIntegrityViolationException} apanhada (que o Spring ja marcou como
 *       rollback-only GLOBAL ao atravessar o proxy do repositorio) terminar num rollback
 *       silencioso em vez de {@code UnexpectedRollbackException} -- porque
 *       {@code AbstractPlatformTransactionManager.commit} verifica o rollback-only LOCAL antes
 *       do global.</li>
 *   <li>O catch de {@link NoTransactionException} existe so para testes unitarios que chamam o
 *       handler directamente, sem proxy de transacao a volta. A presenca de
 *       {@code @Transactional} nos sete handlers cobertos por esta fase e verificada em separado,
 *       por reflexao, nos Planos 03 e 04 -- nao e responsabilidade desta classe.</li>
 * </ul>
 */
public final class RecusaTransacional {

    private RecusaTransacional() {
    }

    public static <T> ResponseEntity<T> recusar(ResponseEntity<T> resposta) {
        try {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        } catch (NoTransactionException e) {
            // Sem transacao a volta (chamada directa em teste unitario, ou um handler que ainda
            // nao e @Transactional) -- nao ha nada para marcar, e isso nao e um erro aqui.
        }
        return resposta;
    }
}
