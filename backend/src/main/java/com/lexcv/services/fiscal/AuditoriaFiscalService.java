package com.lexcv.services.fiscal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexcv.config.UserPrincipal;
import com.lexcv.models.AuditLog;
import com.lexcv.repositories.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Phase 133 (CFG-01/02/06), Plan 04: o ÚNICO escritor dos eventos de auditoria da configuração
 * fiscal em {@code t_audit_log} ({@code entidadeTipo = configuracao_fiscal},
 * {@code entidadeId = ConfiguracaoFiscal.id}).
 *
 * <p>Separado de propósito de {@code AuditoriaRbacService}: a leitura desse serviço
 * ({@code listar} / {@code AuditLogRepository.buscarEventosRbac}) filtra pelos tipos de entidade
 * RBAC; manter as escritas fiscais aqui deixa esse caminho de leitura e o
 * {@code AuditLogImutabilidadeTest} intactos.
 *
 * <p>Privacidade (regra da Phase 128): o {@code detalhe} guarda apenas o nome de apresentação do
 * autor e os NOMES dos campos alterados -- nunca valores de NIF, morada, email ou telefone, nem o
 * endereço de correio do autor.
 *
 * <p>Phase 134 (EMIS-01, R-03): também grava o evento de emissão de um documento fiscal
 * ({@code acao = documento_fiscal_emitir}, {@code entidadeTipo = documento_fiscal},
 * {@code entidadeId = DocumentoFiscal.id}) -- só o nome do autor e o número formatado, nunca
 * valores, NIFs ou o correio do autor.
 *
 * <p>Cada {@code registar*} é {@code @Transactional(propagation = MANDATORY)}: o evento grava na
 * mesma transação da mudança que descreve; uma recusa (exceção) faz rollback e não deixa evento.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditoriaFiscalService {

    public static final String ENTIDADE_TIPO = "configuracao_fiscal";
    public static final String ENTIDADE_TIPO_DOCUMENTO = "documento_fiscal";

    public static final String ACAO_DADOS_ALTERAR = "faturacao_dados_alterar";
    public static final String ACAO_ATIVAR = "faturacao_ativar";
    public static final String ACAO_DESATIVAR = "faturacao_desativar";
    public static final String ACAO_EMAIL_LIGAR = "faturacao_email_ligar";
    public static final String ACAO_EMAIL_DESLIGAR = "faturacao_email_desligar";
    public static final String ACAO_EMITIR = "documento_fiscal_emitir";

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void registarDadosAlterados(UUID tenantId, UserPrincipal autor, UUID configuracaoId,
                                       Set<String> camposAlterados) {
        Map<String, Object> detalhe = new LinkedHashMap<>();
        put(detalhe, "autorNome", nomeDoAutor(autor));
        put(detalhe, "camposAlterados", camposAlterados == null
                ? List.of()
                : camposAlterados.stream().sorted().toList());
        gravar(tenantId, autor, ACAO_DADOS_ALTERAR, ENTIDADE_TIPO, idTexto(configuracaoId), detalhe);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registarAtivacao(UUID tenantId, UserPrincipal autor, UUID configuracaoId) {
        Map<String, Object> detalhe = new LinkedHashMap<>();
        put(detalhe, "autorNome", nomeDoAutor(autor));
        gravar(tenantId, autor, ACAO_ATIVAR, ENTIDADE_TIPO, idTexto(configuracaoId), detalhe);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registarDesativacao(UUID tenantId, UserPrincipal autor, UUID configuracaoId,
                                    boolean envioEmailDesligado) {
        Map<String, Object> detalhe = new LinkedHashMap<>();
        put(detalhe, "autorNome", nomeDoAutor(autor));
        if (envioEmailDesligado) {
            put(detalhe, "envioEmailDesligado", Boolean.TRUE);
        }
        gravar(tenantId, autor, ACAO_DESATIVAR, ENTIDADE_TIPO, idTexto(configuracaoId), detalhe);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registarEmailLigado(UUID tenantId, UserPrincipal autor, UUID configuracaoId) {
        Map<String, Object> detalhe = new LinkedHashMap<>();
        put(detalhe, "autorNome", nomeDoAutor(autor));
        put(detalhe, "declaracaoAceite", Boolean.TRUE);
        gravar(tenantId, autor, ACAO_EMAIL_LIGAR, ENTIDADE_TIPO, idTexto(configuracaoId), detalhe);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registarEmailDesligado(UUID tenantId, UserPrincipal autor, UUID configuracaoId) {
        Map<String, Object> detalhe = new LinkedHashMap<>();
        put(detalhe, "autorNome", nomeDoAutor(autor));
        gravar(tenantId, autor, ACAO_EMAIL_DESLIGAR, ENTIDADE_TIPO, idTexto(configuracaoId), detalhe);
    }

    /**
     * Phase 134 (R-03): evento de emissão, na transação do {@code PagamentoFaturadoService}.
     * Um rollback da emissão leva também o evento.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registarEmissao(UUID tenantId, UserPrincipal autor, UUID documentoId,
                                String numeroFormatado) {
        Map<String, Object> detalhe = new LinkedHashMap<>();
        put(detalhe, "autorNome", nomeDoAutor(autor));
        put(detalhe, "numeroFormatado", numeroFormatado);
        gravar(tenantId, autor, ACAO_EMITIR, ENTIDADE_TIPO_DOCUMENTO,
                documentoId == null ? null : documentoId.toString(), detalhe);
    }

    /**
     * Lê apenas o nome de apresentação do principal (nunca o endereço de correio nem outro dado
     * pessoal). Este comentário evita escrever o nome literal do getter proibido, para não
     * acionar o gate de código-fonte de {@code AuditoriaFiscalServiceTest}.
     */
    private String nomeDoAutor(UserPrincipal autor) {
        return autor == null ? null : autor.getNome();
    }

    private static String idTexto(UUID id) {
        return id == null ? null : id.toString();
    }

    /** Omite valores nulos e coleções vazias. */
    private void put(Map<String, Object> detalhe, String chave, Object valor) {
        if (valor == null) {
            return;
        }
        if (valor instanceof Collection<?> colecao && colecao.isEmpty()) {
            return;
        }
        detalhe.put(chave, valor);
    }

    /**
     * Constrói sempre um {@link AuditLog} novo, sem id (save insere, nunca atualiza).
     * {@code processoId} fica nulo: estes eventos não pertencem a nenhum processo.
     */
    private void gravar(UUID tenantId, UserPrincipal autor, String acao, String entidadeTipo,
                        String entidadeId, Map<String, Object> detalheCampos) {
        AuditLog auditLog = AuditLog.builder()
                .tenantId(tenantId)
                .acao(acao)
                .entidadeTipo(entidadeTipo)
                .entidadeId(entidadeId)
                .autorId(autor == null ? null : autor.getUserId())
                .detalhe(serializarDetalhe(detalheCampos))
                .build();
        auditLogRepository.save(auditLog);
    }

    /**
     * Uma falha de serialização é relançada como {@link IllegalStateException} (não verificada):
     * propaga para fora da transação MANDATORY do chamador e o rollback arrasta também a mudança
     * -- uma mudança nunca faz commit sem o seu evento.
     */
    private String serializarDetalhe(Map<String, Object> detalheCampos) {
        try {
            return objectMapper.writeValueAsString(detalheCampos);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar detalhe do evento de auditoria fiscal", e);
        }
    }
}
