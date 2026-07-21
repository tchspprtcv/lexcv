export type PesquisaResultadoTipo = "cliente" | "processo" | "documento" | "parecer";

export interface ResultadoPesquisa {
  tipo: PesquisaResultadoTipo;
  id: string;
  titulo: string;
  subtitulo?: string;
  rota: string;
}
