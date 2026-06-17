package com.lexcv.dtos;

public record TransicaoRequest(
        String justificativa   // nullable; required only for suspender/encerrar/reabrir
) {}
