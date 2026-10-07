package com.lexcv.jobs;

import com.lexcv.fiscal.email.EmailProperties;
import com.lexcv.fiscal.email.EntregaEmailGateway;
import com.lexcv.services.fiscal.EntregaEmailReclamada;
import com.lexcv.services.fiscal.EntregaEmailTransacoes;
import com.lexcv.services.fiscal.ProcessadorEntregaEmail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Phase 137 (ENTR-03, ENTR-05, ENTR-06): o job do outbox de email fiscal. A cada ~30 s reclama um
 * lote de entregas {@code PENDENTE} devidas (de todos os tenants) e processa cada uma com
 * {@link ProcessadorEntregaEmail#processar}: PDF + XML anexados, envio único depois de renovar o
 * lease, resultado registado.
 *
 * <p><b>Terceiro job periódico</b> (pool do scheduler com 4 threads, 137-09), ao lado do outbox da
 * comunicação ({@link FiscalOutboxJob}) e do job diário das 06:00; não os bloqueia.
 *
 * <p><b>Sem SMTP não faz nada</b> (T-137-82): quando {@link EntregaEmailGateway#configurado()} é
 * {@code false}, a passagem termina antes de qualquer reclamação ou varredura, por isso nenhuma
 * tentativa é gasta e as linhas ficam {@code PENDENTE} até o SMTP ser configurado. Só um registo
 * DEBUG por passagem, para não encher o log de 30 em 30 s.
 *
 * <p><b>Sem utilizador nem transação:</b> corre numa thread do scheduler, sem SecurityContext. O único
 * passo entre tenants é a reclamação; cada linha reclamada traz o seu {@code tenant_id}, e o
 * processador usa só esse. As transações curtas vivem em {@link EntregaEmailTransacoes}; o PDF e o
 * envio SMTP correm fora de qualquer transação.
 *
 * <p><b>Concorrência</b> (T-137-81): {@code fixedDelay} nunca se sobrepõe a si próprio dentro de uma
 * instância; entre instâncias, a reclamação com {@code SKIP LOCKED} mais o lease (renovado mesmo antes
 * do envio) garantem um só dono por linha.
 *
 * <p><b>Isolamento:</b> um {@code catch (Exception)} no topo de {@link #executar()}, outro por item e
 * outro por notificação de esgotada. Um {@link Error} não é engolido: interrompe a passagem e chega
 * ao scheduler do Spring, que o regista e mantém a tarefa agendada.
 */
@Component
@Slf4j
public class EmailFiscalOutboxJob {

    private final EntregaEmailTransacoes transacoes;
    private final ProcessadorEntregaEmail processador;
    private final EntregaEmailGateway gateway;
    private final EmailProperties propriedades;

    public EmailFiscalOutboxJob(EntregaEmailTransacoes transacoes, ProcessadorEntregaEmail processador,
                                EntregaEmailGateway gateway, EmailProperties propriedades) {
        this.transacoes = transacoes;
        this.processador = processador;
        this.gateway = gateway;
        this.propriedades = propriedades;
    }

    @Scheduled(fixedDelayString = "${app.email.outbox.intervalo:PT30S}",
            initialDelayString = "${app.email.outbox.atraso-inicial:PT40S}")
    public void executar() {
        try {
            executarUmaVez();
        } catch (Exception e) {
            log.error("Falha inesperada na execução do job do outbox de email fiscal ({})",
                    e.getClass().getSimpleName());
        }
    }

    /**
     * Uma passagem: sem SMTP devolve 0 sem tocar na base de dados; senão fecha as linhas esgotadas
     * (notificando cada uma), reclama um lote e processa cada item. Devolve o número de itens
     * reclamados.
     */
    int executarUmaVez() {
        if (!gateway.configurado()) {
            log.debug("Outbox de email fiscal: SMTP não configurado; nada a reclamar");
            return 0;
        }
        encerrarEsgotadas();
        EmailProperties.Outbox outbox = propriedades.outbox();
        List<EntregaEmailReclamada> itens = transacoes.reclamar(outbox.lote(), outbox.lease());
        for (EntregaEmailReclamada item : itens) {
            try {
                processador.processar(item);
            } catch (Exception e) {
                log.error("Falha ao processar a entrega de email {} (documento {}): {}", item.id(),
                        item.documentoFiscalId(), e.getClass().getSimpleName());
            }
        }
        return itens.size();
    }

    /**
     * Linhas cujo processamento nunca chegou a registar um resultado deixam de ser reclamadas ao fim
     * do máximo de tentativas; aqui são fechadas em {@code FALHOU} e notificadas. Uma falha desta
     * varredura nunca impede a reclamação do lote.
     */
    private void encerrarEsgotadas() {
        List<EntregaEmailReclamada> esgotadas;
        try {
            esgotadas = transacoes.encerrarEsgotadas();
        } catch (Exception e) {
            log.error("Falha ao encerrar as entregas de email esgotadas ({})", e.getClass().getSimpleName());
            return;
        }
        for (EntregaEmailReclamada item : esgotadas) {
            log.warn("Entrega de email {} (documento {}) esgotou as tentativas sem resultado: FALHOU",
                    item.id(), item.documentoFiscalId());
            try {
                processador.notificarEsgotada(item);
            } catch (Exception e) {
                log.error("Notificação da entrega de email esgotada {} não criada ({})", item.id(),
                        e.getClass().getSimpleName());
            }
        }
    }
}
