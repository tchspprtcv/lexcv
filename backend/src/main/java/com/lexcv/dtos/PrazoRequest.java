package com.lexcv.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record PrazoRequest(
        @NotBlank(message = "A descrição é obrigatória") String descricao,
        @NotNull(message = "A data limite é obrigatória") LocalDate dataLimite,
        String prioridade,      // ALTA | MEDIA | BAIXA; default MEDIA if null
        UUID responsavelId      // optional
) {}
