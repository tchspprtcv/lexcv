export interface DashboardKpis {
  total_clientes: number;
  processos_ativos: number;
  prazos_vencer: number;
  valores_recebidos_mes: number;
}

export interface DashboardBacklogItem {
  responsavel_id: string;
  responsavel_nome: string;
  count: number;
}

export interface ExposicaoCarteiraItem {
  area_juridica: string;
  count: number;
  percentage: number;
}

export interface ProcessosDashboardData {
  operacional: {
    backlog_por_responsavel: DashboardBacklogItem[];
    prazos_criticos_count: number;
    processos_inativos_count: number;
  };
  executivo: {
    conformidade_documental: number;
    exposicao_por_carteira: ExposicaoCarteiraItem[];
    tempo_medio_resolucao_dias: number;
  };
}
