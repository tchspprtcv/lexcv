package com.lexcv.services.fiscal;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.ConfiguracaoFiscalRequest;
import com.lexcv.dtos.ConfiguracaoFiscalResponse;
import com.lexcv.dtos.EmailAutomaticoRequest;
import com.lexcv.dtos.SerieFiscalResponse;
import com.lexcv.exceptions.RecusaFiscalException;
import com.lexcv.fiscal.email.EmailProperties;
import com.lexcv.models.ConfiguracaoFiscal;
import com.lexcv.models.MotivoIsencaoIva;
import com.lexcv.models.RegimeIva;
import com.lexcv.models.User;
import com.lexcv.repositories.ConfiguracaoFiscalRepository;
import com.lexcv.repositories.SerieFiscalRepository;
import com.lexcv.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Phase 133 (CFG-01/02/03/06), Plan 04: regras de negócio da configuração fiscal do escritório.
 *
 * <p>{@code tenantId} e {@code autor} são sempre parâmetros (vêm do principal autenticado no
 * controller, Plan 05); este serviço nunca lê o contexto de segurança e nunca usa um tenant vindo
 * do corpo do pedido.
 *
 * <p>"Já foi emitido um documento" = {@code existsByTenantIdAndUltimoNumeroGreaterThan(tenantId, 0L)}.
 * A Phase 134 mantém esta regra válida porque emitir um documento incrementa a série na mesma
 * transação. Com documentos emitidos, o NIF fica bloqueado e a faturação deixa de poder ser
 * desativada; os restantes campos continuam editáveis.
 *
 * <p>Todas as recusas ({@link RecusaFiscalException}) são lançadas ANTES de qualquer mutação da
 * entidade -- com open-in-view nada fica sujo para um flush -- e a exceção faz rollback da
 * transação, pelo que uma recusa nunca deixa evento de auditoria.
 *
 * <p><b>Concorrência (CR-01 da revisão):</b> as quatro mutações ({@code guardar}, {@code ativar},
 * {@code desativar}, {@code definirEmailAutomatico}) leem a linha com
 * {@link ConfiguracaoFiscalRepository#bloquearPorTenant} ({@code PESSIMISTIC_WRITE}) como PRIMEIRA
 * operação, antes de consultar as séries. Duas mutações concorrentes do mesmo escritório
 * serializam-se: a segunda espera pelo commit da primeira e decide sobre o estado já comprometido,
 * em vez de reescrever a linha inteira a partir de uma cópia desatualizada. Ordem de locks:
 * configuração fiscal -> série fiscal ({@code NumeracaoService}); a emissão da Phase 134 tem de
 * respeitar a mesma ordem. Sem linha (primeiro {@code guardar}) não há nada para bloquear: a
 * corrida é resolvida por {@code uk_configuracao_fiscal_tenant} ({@code CONFIGURACAO_FISCAL_CONCORRENTE}).
 *
 * <p>O interruptor de envio automático por email não tem efeito de envio nesta fase (o envio é a
 * Phase 137); aqui só se guarda a escolha, quem aceitou a declaração e quando.
 */
@Service
@RequiredArgsConstructor
public class ConfiguracaoFiscalService {

    static final String PAIS_CODIGO = "CV";

    private final ConfiguracaoFiscalRepository configuracaoFiscalRepository;
    private final SerieFiscalRepository serieFiscalRepository;
    private final UserRepository userRepository;
    private final AuditoriaFiscalService auditoriaFiscalService;
    private final Clock clock;
    /** Phase 137 (ENTR-06): só se lê {@code configurado()}; nunca host nem credenciais. */
    private final EmailProperties emailProperties;

    // -----------------------------------------------------------------------------------------
    // Leitura
    // -----------------------------------------------------------------------------------------

    /** CFG-03: sem linha devolve tudo desligado e NÃO cria nada. */
    @Transactional(readOnly = true)
    public ConfiguracaoFiscalResponse obter(UUID tenantId) {
        ConfiguracaoFiscal config = configuracaoFiscalRepository.findByTenantId(tenantId).orElse(null);
        return toResponse(tenantId, config, documentosEmitidos(tenantId));
    }

    @Transactional(readOnly = true)
    public List<SerieFiscalResponse> listarSeries(UUID tenantId) {
        return serieFiscalRepository.findByTenantIdOrderByAnoDescTipoDocumentoAsc(tenantId).stream()
                .map(SerieFiscalResponse::de)
                .toList();
    }

    // -----------------------------------------------------------------------------------------
    // Dados fiscais
    // -----------------------------------------------------------------------------------------

    @Transactional
    public ConfiguracaoFiscalResponse guardar(UUID tenantId, UserPrincipal autor, ConfiguracaoFiscalRequest req) {
        String nif = aparar(req.nif());
        String firma = aparar(req.firma());
        String morada = aparar(req.morada());
        String localidade = aparar(req.localidade());
        String email = aparar(req.emailContacto());
        String telefone = aparar(req.telefoneContacto());
        RegimeIva regime = req.regimeIva();
        String motivo = aparar(req.motivoIsencaoCodigo());

        if (regime == RegimeIva.ISENTO) {
            if (MotivoIsencaoIva.porCodigo(motivo).isEmpty()) {
                throw new RecusaFiscalException(HttpStatus.BAD_REQUEST, "MOTIVO_ISENCAO_INVALIDO",
                        "Escolha o motivo de isenção.", "motivoIsencaoCodigo");
            }
        } else {
            motivo = null;
        }

        ConfiguracaoFiscal existente = configuracaoFiscalRepository.bloquearPorTenant(tenantId).orElse(null);
        boolean documentosEmitidos = documentosEmitidos(tenantId);
        boolean nifMuda = existente == null || !Objects.equals(existente.getNif(), nif);

        if (existente != null && nifMuda && documentosEmitidos) {
            throw new RecusaFiscalException(HttpStatus.CONFLICT, "NIF_BLOQUEADO",
                    "O NIF não pode ser alterado depois de emitido o primeiro documento.", "nif");
        }
        // Sem verificação de unicidade do NIF entre escritórios (WR-05 da revisão, decisão do
        // utilizador): cada escritório regista o seu NIF sem consultar os outros tenants.

        Set<String> camposAlterados = new LinkedHashSet<>();
        if (existente == null) {
            camposAlterados.addAll(List.of("nif", "firma", "morada", "localidade", "emailContacto",
                    "telefoneContacto", "regimeIva", "motivoIsencaoCodigo"));
        } else {
            marcar(camposAlterados, "nif", existente.getNif(), nif);
            marcar(camposAlterados, "firma", existente.getFirma(), firma);
            marcar(camposAlterados, "morada", existente.getMorada(), morada);
            marcar(camposAlterados, "localidade", existente.getLocalidade(), localidade);
            marcar(camposAlterados, "emailContacto", existente.getEmailContacto(), email);
            marcar(camposAlterados, "telefoneContacto", existente.getTelefoneContacto(), telefone);
            marcar(camposAlterados, "regimeIva", existente.getRegimeIva(), regime);
            marcar(camposAlterados, "motivoIsencaoCodigo", existente.getMotivoIsencaoCodigo(), motivo);
        }
        if (camposAlterados.isEmpty()) {
            return toResponse(tenantId, existente, documentosEmitidos);
        }

        Instant agora = clock.instant();
        ConfiguracaoFiscal config = existente;
        if (config == null) {
            config = ConfiguracaoFiscal.builder()
                    .tenantId(tenantId)
                    .ativa(false)
                    .envioEmailAutomatico(false)
                    .createdAt(agora)
                    .build();
        }
        config.setNif(nif);
        config.setFirma(firma);
        config.setMorada(morada);
        config.setLocalidade(localidade);
        config.setPaisCodigo(PAIS_CODIGO);
        config.setEmailContacto(email);
        config.setTelefoneContacto(telefone);
        config.setRegimeIva(regime);
        config.setMotivoIsencaoCodigo(motivo);
        config.setUpdatedAt(agora);
        config.setUpdatedBy(autor.getUserId());

        ConfiguracaoFiscal salvo;
        try {
            salvo = configuracaoFiscalRepository.saveAndFlush(config);
        } catch (DataIntegrityViolationException e) {
            // Dois primeiros "guardar" concorrentes colidem em uk_configuracao_fiscal_tenant.
            throw new RecusaFiscalException(HttpStatus.CONFLICT, "CONFIGURACAO_FISCAL_CONCORRENTE",
                    "Os dados fiscais foram alterados ao mesmo tempo noutro pedido. "
                            + "Atualize a página e tente de novo.");
        }
        auditoriaFiscalService.registarDadosAlterados(tenantId, autor, salvo.getId(), camposAlterados);
        return toResponse(tenantId, salvo, documentosEmitidos);
    }

    // -----------------------------------------------------------------------------------------
    // Ativação
    // -----------------------------------------------------------------------------------------

    @Transactional
    public ConfiguracaoFiscalResponse ativar(UUID tenantId, UserPrincipal autor) {
        ConfiguracaoFiscal config = configuracaoFiscalRepository.bloquearPorTenant(tenantId).orElse(null);
        if (config == null || !config.completa()) {
            throw new RecusaFiscalException(HttpStatus.UNPROCESSABLE_ENTITY, "CONFIGURACAO_FISCAL_INCOMPLETA",
                    "Não foi possível ativar a faturação: os dados fiscais estão incompletos. "
                            + "Preencha e guarde todos os campos e tente de novo.");
        }
        boolean documentosEmitidos = documentosEmitidos(tenantId);
        if (Boolean.TRUE.equals(config.getAtiva())) {
            return toResponse(tenantId, config, documentosEmitidos);
        }
        config.setAtiva(true);
        tocar(config, autor);
        ConfiguracaoFiscal salvo = configuracaoFiscalRepository.save(config);
        auditoriaFiscalService.registarAtivacao(tenantId, autor, salvo.getId());
        return toResponse(tenantId, salvo, documentosEmitidos);
    }

    @Transactional
    public ConfiguracaoFiscalResponse desativar(UUID tenantId, UserPrincipal autor) {
        ConfiguracaoFiscal config = configuracaoFiscalRepository.bloquearPorTenant(tenantId).orElse(null);
        boolean documentosEmitidos = documentosEmitidos(tenantId);
        if (config == null || !Boolean.TRUE.equals(config.getAtiva())) {
            return toResponse(tenantId, config, documentosEmitidos);
        }
        if (documentosEmitidos) {
            throw new RecusaFiscalException(HttpStatus.CONFLICT, "FATURACAO_JA_EMITIU",
                    "Não é possível desativar a faturação porque já foram emitidos documentos.");
        }
        boolean eraLigado = Boolean.TRUE.equals(config.getEnvioEmailAutomatico());
        config.setAtiva(false);
        // Invariante: envio automático ligado implica faturação ativa.
        config.setEnvioEmailAutomatico(false);
        tocar(config, autor);
        ConfiguracaoFiscal salvo = configuracaoFiscalRepository.save(config);
        auditoriaFiscalService.registarDesativacao(tenantId, autor, salvo.getId(), eraLigado);
        return toResponse(tenantId, salvo, documentosEmitidos);
    }

    // -----------------------------------------------------------------------------------------
    // Interruptor de email (CFG-06)
    // -----------------------------------------------------------------------------------------

    @Transactional
    public ConfiguracaoFiscalResponse definirEmailAutomatico(UUID tenantId, UserPrincipal autor,
                                                             EmailAutomaticoRequest req) {
        ConfiguracaoFiscal config = configuracaoFiscalRepository.bloquearPorTenant(tenantId).orElse(null);
        boolean documentosEmitidos = documentosEmitidos(tenantId);
        boolean ligado = Boolean.TRUE.equals(config == null ? null : config.getEnvioEmailAutomatico());

        if (Boolean.TRUE.equals(req.ligado())) {
            if (config == null || !Boolean.TRUE.equals(config.getAtiva())) {
                throw new RecusaFiscalException(HttpStatus.CONFLICT, "FATURACAO_DESLIGADA",
                        "Ative a faturação para poder ligar o envio automático.");
            }
            if (!Boolean.TRUE.equals(req.aceiteDeclaracao())) {
                throw new RecusaFiscalException(HttpStatus.UNPROCESSABLE_ENTITY, "DECLARACAO_NAO_ACEITE",
                        "É necessário confirmar que compreende que os documentos simulados não têm validade fiscal.",
                        "aceiteDeclaracao");
            }
            if (ligado) {
                return toResponse(tenantId, config, documentosEmitidos);
            }
            config.setEnvioEmailAutomatico(true);
            config.setEnvioEmailAceitePor(autor.getUserId());
            config.setEnvioEmailAceiteEm(clock.instant());
            tocar(config, autor);
            ConfiguracaoFiscal salvo = configuracaoFiscalRepository.save(config);
            auditoriaFiscalService.registarEmailLigado(tenantId, autor, salvo.getId());
            return toResponse(tenantId, salvo, documentosEmitidos);
        }

        if (!ligado) {
            return toResponse(tenantId, config, documentosEmitidos);
        }
        // Mantém envioEmailAceitePor/Em como histórico da última aceitação.
        config.setEnvioEmailAutomatico(false);
        tocar(config, autor);
        ConfiguracaoFiscal salvo = configuracaoFiscalRepository.save(config);
        auditoriaFiscalService.registarEmailDesligado(tenantId, autor, salvo.getId());
        return toResponse(tenantId, salvo, documentosEmitidos);
    }

    // -----------------------------------------------------------------------------------------
    // Auxiliares
    // -----------------------------------------------------------------------------------------

    private boolean documentosEmitidos(UUID tenantId) {
        return serieFiscalRepository.existsByTenantIdAndUltimoNumeroGreaterThan(tenantId, 0L);
    }

    private void tocar(ConfiguracaoFiscal config, UserPrincipal autor) {
        config.setUpdatedAt(clock.instant());
        config.setUpdatedBy(autor.getUserId());
    }

    private static String aparar(String valor) {
        return valor == null ? null : valor.trim();
    }

    private static void marcar(Set<String> alterados, String campo, Object antes, Object depois) {
        if (!Objects.equals(antes, depois)) {
            alterados.add(campo);
        }
    }

    private ConfiguracaoFiscalResponse toResponse(UUID tenantId, ConfiguracaoFiscal c, boolean documentosEmitidos) {
        if (c == null) {
            return new ConfiguracaoFiscalResponse(false, null, null, null, null, ConfiguracaoFiscalResponse.PAIS,
                    null, null, null, null, false, false, documentosEmitidos, documentosEmitidos, false,
                    false, null, null, emailProperties.configurado());
        }
        boolean ativa = Boolean.TRUE.equals(c.getAtiva());
        boolean email = Boolean.TRUE.equals(c.getEnvioEmailAutomatico());
        String aceitePorNome = null;
        Instant aceiteEm = null;
        if (email) {
            aceiteEm = c.getEnvioEmailAceiteEm();
            if (c.getEnvioEmailAceitePor() != null) {
                aceitePorNome = userRepository.findById(c.getEnvioEmailAceitePor())
                        .filter(u -> tenantId.equals(u.getTenantId()))
                        .map(User::getNome)
                        .orElse(null);
            }
        }
        return new ConfiguracaoFiscalResponse(
                true,
                c.getNif(),
                c.getFirma(),
                c.getMorada(),
                c.getLocalidade(),
                ConfiguracaoFiscalResponse.PAIS,
                c.getEmailContacto(),
                c.getTelefoneContacto(),
                c.getRegimeIva(),
                c.getMotivoIsencaoCodigo(),
                c.completa(),
                ativa,
                documentosEmitidos,
                documentosEmitidos,
                ativa && !documentosEmitidos,
                email,
                aceitePorNome,
                aceiteEm,
                emailProperties.configurado());
    }
}
