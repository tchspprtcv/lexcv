package com.lexcv.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessosDashboardData {
    private OperacionalData operacional;
    private ExecutivoData executivo;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OperacionalData {
        private List<DashboardBacklogItem> backlog_por_responsavel;
        private long prazos_criticos_count;
        private long processos_inativos_count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutivoData {
        private long conformidade_documental;
        private List<ExposicaoCarteiraItem> exposicao_por_carteira;
        private long tempo_medio_resolucao_dias;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardBacklogItem {
        private String responsavel_id;
        private String responsavel_nome;
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExposicaoCarteiraItem {
        private String area_juridica;
        private long count;
        private long percentage;
    }
}
