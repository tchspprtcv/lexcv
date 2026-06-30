package com.lexcv.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DadosTipo {
    // Particular
    private Integer idade;
    private String sexo;
    private String nacionalidade;
    @JsonProperty("bi_passaporte")
    private String biPassaporte;

    // Empresa
    @JsonProperty("nome_comercial")
    private String nomeComercial;
    private String nif;
    private String sede;
    @JsonProperty("representante_legal")
    private String representanteLegal;
    @JsonProperty("cargo")
    private String cargoRepresentante;
}
