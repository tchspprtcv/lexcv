package com.lexcv.jobs;

import com.lexcv.fiscal.efatura.EfaturaProperties;
import com.lexcv.services.fiscal.ComunicacaoFiscalTransacoes;
import com.lexcv.services.fiscal.ComunicacaoReclamada;
import com.lexcv.services.fiscal.ProcessadorComunicacaoFiscal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Phase 136 (DFE-04): o job do outbox fiscal. A cada ~30 s reclama um lote de comunicações
 * {@code PENDENTE} devidas (de TODOS os tenants, incluindo suspensos) e processa cada uma com
 * {@link ProcessadorComunicacaoFiscal#processar}.
 *
 * <p><b>Desacoplado da emissão:</b> a transação de emissão (Phases 134/135) não é tocada. Ela só
 * cria a linha {@code PENDENTE}, que é a entrada do outbox; o XML, o IUD e a comunicação
 * acontecem aqui, em segundo plano, sem atrasar o registo do pagamento.
 *
 * <p><b>Sem utilizador:</b> corre numa thread do scheduler, sem SecurityContext. Não itera
 * tenants: cada linha reclamada traz o seu {@code tenant_id}, e o processador usa só esse.
 *
 * <p><b>Concorrência:</b> {@code fixedDelay} nunca se sobrepõe a si próprio dentro de uma
 * instância (a próxima execução só começa depois de a anterior acabar). Entre várias instâncias,
 * a reclamação com bloqueio de linha que salta as já bloqueadas, mais o lease, garante que cada
 * linha tem um só dono. O pool do scheduler tem 3 threads (136-12), por isso este job não bloqueia
 * o job diário das 06:00.
 *
 * <p><b>Isolamento:</b> um {@code catch (Exception)} no topo de {@link #executar()} e outro por item,
 * para que um item com falha nunca pare o lote. IN-08 da revisão: um {@link Error} (ex.
 * {@code OutOfMemoryError}, {@code StackOverflowError}) já não é engolido -- interrompe esta
 * passagem e chega ao scheduler do Spring, que o regista e mantém a tarefa periódica agendada; o
 * item interrompido fica para a próxima reclamação (limitada por WR-01). Sem anotações de transação: as transações curtas vivem
 * em {@link ComunicacaoFiscalTransacoes}.
 */
@Component
@Slf4j
public class FiscalOutboxJob {

    private final ComunicacaoFiscalTransacoes transacoes;
    private final ProcessadorComunicacaoFiscal processador;
    private final EfaturaProperties propriedades;

    public FiscalOutboxJob(ComunicacaoFiscalTransacoes transacoes, ProcessadorComunicacaoFiscal processador,
                           EfaturaProperties propriedades) {
        this.transacoes = transacoes;
        this.processador = processador;
        this.propriedades = propriedades;
    }

    @Scheduled(fixedDelayString = "${app.efatura.outbox.intervalo:PT30S}",
            initialDelayString = "${app.efatura.outbox.atraso-inicial:PT20S}")
    public void executar() {
        try {
            executarUmaVez();
        } catch (Exception e) {
            log.error("Falha inesperada na execução do job do outbox fiscal", e);
        }
    }

    /**
     * Uma passagem: fecha as linhas esgotadas (WR-01), reclama um lote e processa cada item. Devolve o
     * número de itens reclamados.
     */
    int executarUmaVez() {
        encerrarEsgotadas();
        EfaturaProperties.Outbox outbox = propriedades.outbox();
        List<ComunicacaoReclamada> itens = transacoes.reclamar(outbox.lote(), outbox.lease());
        for (ComunicacaoReclamada item : itens) {
            try {
                processador.processar(item);
            } catch (Exception e) {
                log.error("Falha ao processar a comunicação fiscal {} (documento {})", item.id(),
                        item.documentoFiscalId(), e);
            }
        }
        return itens.size();
    }

    /**
     * WR-01: uma linha cujo processamento nunca chega a registar um resultado (o worker morre, o
     * gateway fica pendurado para lá do lease, o registo falha sempre) deixa de ser reclamada ao fim
     * de {@code MAX_TENTATIVAS} reclamações; aqui é fechada em {@code ERRO} e notificada. Uma falha
     * desta varredura nunca impede a reclamação do lote.
     */
    private void encerrarEsgotadas() {
        List<ComunicacaoReclamada> esgotadas;
        try {
            esgotadas = transacoes.encerrarEsgotadas();
        } catch (Exception e) {
            log.error("Falha ao encerrar as comunicações fiscais esgotadas", e);
            return;
        }
        for (ComunicacaoReclamada item : esgotadas) {
            log.warn("Comunicação fiscal {} (documento {}) esgotou as tentativas sem resultado: ERRO",
                    item.id(), item.documentoFiscalId());
            try {
                processador.notificarEsgotada(item);
            } catch (Exception e) {
                log.error("Notificação da comunicação fiscal esgotada {} não criada", item.id(), e);
            }
        }
    }
}
