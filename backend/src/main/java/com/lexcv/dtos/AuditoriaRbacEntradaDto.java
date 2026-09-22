package com.lexcv.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

// Phase 128 (AUDT-01/AUDT-02), Plan 02: a forma de leitura resolvida que
// AuditoriaRbacService.listar devolve. O backend resolve os campos estruturados (categoria,
// alvoId, nomes vindos do detalhe JSON); o frontend compoe a frase em portugues de Cabo Verde
// ("Maria Silva retirou o papel Advogado a Joao Pires" -- ver 128-UI-SPEC.md, Open Discretion
// Note 2). "categoria" existe precisamente para que o frontend nunca precise de interpretar
// entidade_tipo -- ver AuditoriaRbacService.listar, que a resolve a partir de "papel_escritorio"
// (-> "papel") ou "atribuicao_papel" (-> "atribuicao").
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditoriaRbacEntradaDto {

    private Long id;
    private LocalDateTime timestamp;
    private String acao;

    // "papel" | "atribuicao" -- ver o comentario da classe.
    private String categoria;

    // Nome do autor no momento do evento (snapshot, Decisao 2 revista, 128-CONTEXT.md). Nulo
    // quando o evento nao teve autor humano (autorId nulo, ex.: provisionamento de sistema).
    private String autorNome;

    // Preenchido apenas para categoria "atribuicao" -- id do utilizador alvo (entidade_id).
    private String alvoId;

    // Nome do alvo no momento do evento (snapshot). Nulo para eventos de categoria "papel".
    private String alvoNome;

    private String papelId;
    private String papelNome;

    // Preenchidos apenas em papel_renomear.
    private String nomeAntigo;
    private String nomeNovo;

    // Preenchidos apenas em papel_criar/papel_apagar/papel_permissoes_alterar. Nulo quando o
    // detalhe nao tem a chave (nunca uma lista vazia proveniente de omissao -- ver
    // AuditoriaRbacService: chaves vazias sao omitidas na escrita).
    private List<String> permissoesAdicionadas;
    private List<String> permissoesRemovidas;

    // Preenchido apenas em papel_retirar por eliminacao de utilizador
    // (AuditoriaRbacService.MOTIVO_UTILIZADOR_ELIMINADO) ou por provisionamento
    // (MOTIVO_PROVISIONAMENTO).
    private String motivo;
}
