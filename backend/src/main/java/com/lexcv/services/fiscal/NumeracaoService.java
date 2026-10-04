package com.lexcv.services.fiscal;

import com.lexcv.exceptions.RecusaFiscalException;
import com.lexcv.models.AmbienteFiscal;
import com.lexcv.models.SerieFiscal;
import com.lexcv.models.TipoDocumentoFiscal;
import com.lexcv.repositories.SerieFiscalRepository;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

/**
 * Phase 133 (CFG-05): numeração fiscal sequencial, sem lacunas nem duplicados, por série
 * (tenant, tipo de documento, ano civil, ambiente).
 *
 * <p><b>Como:</b> {@code lock_timeout} local à transação (5s) -> {@code INSERT ... ON CONFLICT DO
 * NOTHING} da série -> {@code SELECT ... FOR UPDATE} da linha -> {@code refresh} da entidade ->
 * incremento de {@code ultimo_numero} (dirty checking; o flush acontece no commit do chamador).
 * O {@code refresh} (WR-02 da revisão) é obrigatório: se o chamador já tinha a série no
 * persistence context (lida sem lock antes), o {@code FOR UPDATE} devolve essa instância sem a
 * reidratar, e o incremento partiria de um valor desatualizado (número duplicado). Nunca usar uma
 * SEQUENCE (perde números em rollback), MAX()+1 (duplica sob concorrência), um monitor da JVM
 * (não protege várias instâncias) nem uma transação própria (o número faria commit sem o
 * documento) -- PITFALLS P-01.
 *
 * <p><b>MANDATORY:</b> o número e o documento fazem commit ou rollback juntos; chamar fora de
 * uma transação lança {@link org.springframework.transaction.IllegalTransactionStateException}
 * (mesmo racional de {@code AuditoriaRbacService}).
 *
 * <p><b>Ordem de locks (Phase 134+):</b> configuração fiscal primeiro
 * ({@code ConfiguracaoFiscalRepository.bloquearPorTenant}, CR-01 da revisão), depois conta
 * corrente, série por último (ARCHITECTURE §4.2). O lock da série tem de ser o ÚLTIMO lock tomado na transação, para ser
 * mantido o menor tempo possível e nunca participar num deadlock.
 *
 * <p><b>Rede de segurança:</b> {@code UNIQUE(tenant_id, serie_id, numero)} na tabela de documentos
 * da Phase 134.
 *
 * <p>O serviço nunca lê o SecurityContext: o {@code tenantId} é parâmetro, para que o emissor da
 * plataforma (Phase 138) o reutilize.
 */
@Service
@Slf4j
public class NumeracaoService {

    private static final ZoneId FUSO_CABO_VERDE = ZoneId.of("Atlantic/Cape_Verde");

    private final SerieFiscalRepository serieFiscalRepository;
    private final Clock clock;

    public NumeracaoService(SerieFiscalRepository serieFiscalRepository, Clock clock) {
        this.serieFiscalRepository = serieFiscalRepository;
        this.clock = clock;
    }

    /**
     * Atribui o próximo número da série corrente (ano civil de Cabo Verde segundo o
     * {@link Clock}). O lock da série fica retido até ao fim da transação do chamador.
     *
     * @throws RecusaFiscalException {@code SERIE_INDISPONIVEL} (503) se o lock não for obtido em 5s
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public NumeroFiscalAtribuido proximoNumero(UUID tenantId, TipoDocumentoFiscal tipo, AmbienteFiscal ambiente) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(ambiente, "ambiente");

        LocalDate dataEmissao = LocalDate.now(clock.withZone(FUSO_CABO_VERDE));
        int ano = dataEmissao.getYear();
        String codigo = SerieFiscal.gerarCodigo(tipo, ano, ambiente);

        SerieFiscal serie;
        try {
            serieFiscalRepository.definirLockTimeoutLocal();
            serieFiscalRepository.criarSeNaoExiste(UUID.randomUUID(), tenantId, tipo.name(), ano,
                    ambiente.name(), codigo);
            serie = serieFiscalRepository.bloquear(tenantId, tipo, ano, ambiente)
                    .orElseThrow(() -> new IllegalStateException(
                            "Série fiscal inexistente depois do INSERT ON CONFLICT: " + codigo));
        } catch (PessimisticLockingFailureException | PessimisticLockException | LockTimeoutException e) {
            log.warn("Lock da série fiscal não obtido: tenantId={} tipo={} ano={}", tenantId, tipo, ano);
            throw new RecusaFiscalException(HttpStatus.SERVICE_UNAVAILABLE, "SERIE_INDISPONIVEL",
                    "A série de numeração está ocupada. Tente novamente dentro de instantes.");
        }

        // WR-02: a linha já está bloqueada; o refresh lê o valor comprometido mesmo que a
        // instância já estivesse gerida (e desatualizada) no persistence context do chamador.
        serieFiscalRepository.refrescar(serie);
        long numero = serie.getUltimoNumero() + 1;
        serie.setUltimoNumero(numero);
        return new NumeroFiscalAtribuido(serie.getId(), serie.getCodigo(), ano, numero, dataEmissao);
    }
}
