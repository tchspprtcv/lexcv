package com.lexcv.models;

import com.fasterxml.jackson.annotation.JsonInclude;
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
    private String biPassaporte;

    // Empresa
    private String nomeComercial;
    private String nif;
    private String sede;
    private String representanteLegal;
    private String cargoRepresentante;
}
