package com.lexcv.dtos;

import java.util.List;
import java.util.UUID;

public record WorkflowResponse(
        String estadoAtual,
        UUID responsavelId,
        String responsavelNome,    // resolved from User; null -> "Não atribuído" in UI
        String proximoPasso,       // derived from state map; null -> "—" in UI
        List<TransicaoInfo> transicoesDisponiveis
) {
    public record TransicaoInfo(
            String acao,                    // "ativar" | "suspender" | "encerrar" | "reabrir"
            String label,                   // "Ativar Processo" | "Suspender" | "Encerrar" | "Reabrir"
            String permissaoNecessaria,     // "processos:edit" | "processos:manage"
            boolean requerJustificativa
    ) {}
}
