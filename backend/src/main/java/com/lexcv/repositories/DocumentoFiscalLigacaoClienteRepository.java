package com.lexcv.repositories;

import com.lexcv.models.DocumentoFiscal;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/**
 * Phase 134 (D-15): a ÚNICA escrita sobre {@code t_documento_fiscal} além do INSERT.
 *
 * <p>Na fusão de clientes, os documentos do cliente absorvido passam a apontar para o cliente
 * que fica. É nativo de propósito: {@code @Immutable} ignora alterações por dirty-checking e um
 * UPDATE JPQL sobre uma entidade imutável apenas emite um aviso. Só {@code cliente_id} muda --
 * as colunas da fotografia do adquirente ({@code adquirente_*}) nunca são tocadas, o documento
 * continua a mostrar os dados com que foi emitido.
 *
 * <p>O chamador (fusão) tem de deter os locks das DUAS linhas de cliente antes de chamar este
 * método. A SQL está fixada por {@code DocumentoFiscalImutabilidadeTest}.
 */
public interface DocumentoFiscalLigacaoClienteRepository extends Repository<DocumentoFiscal, UUID> {

    @Modifying
    @Query(nativeQuery = true,
            value = "UPDATE t_documento_fiscal SET cliente_id = :novo WHERE tenant_id = :tenantId AND cliente_id = :antigo")
    int repontarCliente(@Param("tenantId") UUID tenantId,
                        @Param("antigo") UUID antigo,
                        @Param("novo") UUID novo);
}
