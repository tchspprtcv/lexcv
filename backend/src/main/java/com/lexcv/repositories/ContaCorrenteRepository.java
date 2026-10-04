package com.lexcv.repositories;

import com.lexcv.models.ContaCorrente;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import java.util.Optional;
import java.util.UUID;

public interface ContaCorrenteRepository extends JpaRepository<ContaCorrente, Integer> {

    /** Caminho legado (pagamentos sem faturação): leitura sem lock. */
    Optional<ContaCorrente> findByClienteId(UUID clienteId);

    /**
     * Phase 134 (R-01): {@code SELECT ... FOR UPDATE} da conta corrente do cliente.
     *
     * <p><b>Ordem global de locks:</b> configuração → cliente → processo → conta corrente →
     * série. Tem de ser a PRIMEIRA leitura desta linha na transação (com OSIV, uma instância já
     * carregada sem lock ficaria desatualizada). A linha não tem {@code tenant_id}: o chamador
     * já validou e bloqueou o cliente do seu tenant antes. Criar a linha em falta com
     * {@link #criarSeNaoExiste} antes de bloquear.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ContaCorrente c where c.clienteId = :clienteId")
    Optional<ContaCorrente> bloquearPorCliente(@Param("clienteId") UUID clienteId);

    /**
     * Phase 134: cria a conta corrente a zero sem corrida (depende de
     * {@code uk_conta_corrente_cliente}/unique em {@code cliente_id}). Duas transações
     * concorrentes nunca criam duas linhas; a segunda não faz nada. Segue-se
     * {@link #bloquearPorCliente} (ordem R-01).
     *
     * <p>{@code @Transactional} (REQUIRED) só para os chamadores sem transação própria (o GET da
     * conta corrente, WR-01 da revisão); dentro da emissão junta-se à transação dela.
     */
    @Transactional
    @Modifying
    @Query(nativeQuery = true, value = "INSERT INTO t_conta_corrente (cliente_id, saldo, updated_at) "
            + "VALUES (:clienteId, 0, now()) ON CONFLICT (cliente_id) DO NOTHING")
    int criarSeNaoExiste(@Param("clienteId") UUID clienteId);

    /**
     * Phase 134 (WR-01 da revisão): débito ATÓMICO e relativo ({@code saldo = saldo - :valor}),
     * para quem não tem transação própria (ex.: apagar um pagamento legado). O UPDATE toma o lock
     * da linha durante a instrução, espera por uma emissão que a tenha bloqueada e aplica-se ao
     * saldo já confirmado, por isso nunca sobrescreve um crédito concorrente (ao contrário de ler,
     * subtrair e gravar o valor absoluto). Só toma este lock, logo não cria ciclo na ordem R-01.
     *
     * @return número de linhas atualizadas (0 se o cliente não tiver conta corrente)
     */
    @Transactional
    @Modifying
    @Query(nativeQuery = true, value = "UPDATE t_conta_corrente SET saldo = saldo - :valor, updated_at = now() "
            + "WHERE cliente_id = :clienteId")
    int debitar(@Param("clienteId") UUID clienteId, @Param("valor") BigDecimal valor);
}
