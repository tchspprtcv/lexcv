package com.lexcv.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class RiscoPrazoService {

    // Valores possíveis de risco — únicas constantes de referência (WR-03, 85-REVIEW.md).
    // Call sites devem referenciar RiscoPrazoService.OK/.PROXIMO/.VENCIDO em vez de
    // retiparem os literais, para evitar drift silencioso (ex.: um typo "vencid0").
    public static final String OK = "ok";
    public static final String PROXIMO = "proximo";
    public static final String VENCIDO = "vencido";

    // 3-arg: `hoje` injetável — usado pela Phase 88 para determinismo em testes.
    // É esta a implementação "real"; os wrappers de 2 args delegam nela.
    public String computeRisco(LocalDate dataLimite, String prioridade, LocalDate hoje) {
        Objects.requireNonNull(hoje, "hoje não pode ser nulo");
        if (dataLimite == null) return OK;
        if (dataLimite.isBefore(hoje)) return VENCIDO;
        long diasRestantes = ChronoUnit.DAYS.between(hoje, dataLimite);
        int limiarProximo = "ALTA".equalsIgnoreCase(prioridade) ? 7 : 3;
        return diasRestantes <= limiarProximo ? PROXIMO : OK;
    }

    // 2-arg: wrapper de conveniência — comportamento byte-idêntico ao atual (default hoje = now).
    public String computeRisco(LocalDate dataLimite, String prioridade) {
        return computeRisco(dataLimite, prioridade, LocalDate.now());
    }

    // Evento aceita LocalDateTime — converte para LocalDate e reutiliza a MESMA tabela de limiares.
    public String computeRiscoEvento(LocalDateTime dataEvento, String prioridade, LocalDate hoje) {
        return computeRisco(dataEvento == null ? null : dataEvento.toLocalDate(), prioridade, hoje);
    }

    public String computeRiscoEvento(LocalDateTime dataEvento, String prioridade) {
        return computeRiscoEvento(dataEvento, prioridade, LocalDate.now());
    }
}
