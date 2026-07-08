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

    // 3-arg: `hoje` injetável — usado pela Phase 88 para determinismo em testes.
    // É esta a implementação "real"; os wrappers de 2 args delegam nela.
    public String computeRisco(LocalDate dataLimite, String prioridade, LocalDate hoje) {
        Objects.requireNonNull(hoje, "hoje não pode ser nulo");
        if (dataLimite == null) return "ok";
        if (dataLimite.isBefore(hoje)) return "vencido";
        long diasRestantes = ChronoUnit.DAYS.between(hoje, dataLimite);
        int limiarProximo = "ALTA".equalsIgnoreCase(prioridade) ? 7 : 3;
        return diasRestantes <= limiarProximo ? "proximo" : "ok";
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
