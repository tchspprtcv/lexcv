package com.lexcv.dtos;

import com.lexcv.models.ConfiguracaoFiscal;
import com.lexcv.models.RegimeIva;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Phase 133 (CFG-01): corpo de {@code PUT /api/v1/faturacao/configuracao} (Plan 05).
 *
 * <p>Só os 8 campos editáveis dos dados fiscais (T-133-15): tenant, estado de ativação, envio de
 * email e campos de auditoria nunca vêm do corpo. As mensagens são o texto exato do UI-SPEC. A
 * regra cruzada "ISENTO exige motivo oficial" é aplicada no serviço
 * ({@code MOTIVO_ISENCAO_INVALIDO}).
 */
public record ConfiguracaoFiscalRequest(
        @NotBlank(message = "Preencha este campo.")
        @Pattern(regexp = ConfiguracaoFiscal.NIF_FISCAL_REGEX,
                 message = "O NIF deve ter 9 dígitos e começar por um algarismo de 1 a 9.")
        String nif,

        @NotBlank(message = "Preencha este campo.")
        @Size(max = 200, message = "A firma não pode ter mais de 200 caracteres.")
        String firma,

        @NotBlank(message = "Preencha este campo.")
        @Size(max = 100, message = "A morada não pode ter mais de 100 caracteres.")
        String morada,

        @NotBlank(message = "Preencha este campo.")
        @Size(max = 100, message = "A localidade não pode ter mais de 100 caracteres.")
        String localidade,

        @NotBlank(message = "Preencha este campo.")
        @Email(message = "Introduza um email válido.")
        @Size(max = 254, message = "O email não pode ter mais de 254 caracteres.")
        String emailContacto,

        @NotBlank(message = "Preencha este campo.")
        @Size(max = 32, message = "O telefone não pode ter mais de 32 caracteres.")
        String telefoneContacto,

        @NotNull(message = "Preencha este campo.")
        RegimeIva regimeIva,

        String motivoIsencaoCodigo
) {
}
