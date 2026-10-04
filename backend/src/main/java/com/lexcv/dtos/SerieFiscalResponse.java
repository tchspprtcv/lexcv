package com.lexcv.dtos;

import com.lexcv.models.AmbienteFiscal;
import com.lexcv.models.SerieFiscal;
import com.lexcv.models.TipoDocumentoFiscal;

/** Phase 133 (CFG-05): leitura de uma série de numeração fiscal do tenant. */
public record SerieFiscalResponse(
        TipoDocumentoFiscal tipoDocumento,
        String tipoDocumentoRotulo,
        int ano,
        String codigo,
        long ultimoNumero,
        AmbienteFiscal ambiente,
        String ambienteRotulo
) {
    public static SerieFiscalResponse de(SerieFiscal serie) {
        return new SerieFiscalResponse(
                serie.getTipoDocumento(),
                serie.getTipoDocumento().rotulo(),
                serie.getAno(),
                serie.getCodigo(),
                serie.getUltimoNumero() == null ? 0L : serie.getUltimoNumero(),
                serie.getAmbiente(),
                serie.getAmbiente().rotulo());
    }
}
