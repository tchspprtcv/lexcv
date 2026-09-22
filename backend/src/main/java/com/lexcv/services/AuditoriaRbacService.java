package com.lexcv.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexcv.config.UserPrincipal;
import com.lexcv.models.AuditLog;
import com.lexcv.models.Permission;
import com.lexcv.models.TenantRole;
import com.lexcv.models.User;
import com.lexcv.repositories.AuditLogRepository;
import com.lexcv.dtos.AuditoriaRbacEntradaDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Phase 128 (AUDT-01/AUDT-02), Plan 02: o UNICO escritor e leitor dos eventos de auditoria RBAC
 * em {@code t_audit_log}. Nenhum controller deve construir um {@link AuditLog} de RBAC
 * diretamente -- manter isto assim e o que torna a regra de privacidade da Decisao 2 revista
 * (128-CONTEXT.md: {@code detalhe} guarda apenas {@link User#getNome()}, nunca email) aplicavel
 * num unico sitio e testavel aqui, em vez de replicada (e potencialmente esquecida) em cada
 * ponto de escrita.
 *
 * <p>Cada metodo {@code registar*} e {@code @Transactional(propagation = MANDATORY)}: um evento
 * de auditoria tem de aderir a transacao do chamador e nunca fazer commit por conta propria
 * (Decisao 3, 128-CONTEXT.md -- "o evento e a mudanca gravam na mesma transacao"). MANDATORY
 * transforma uma fronteira transacional em falta numa {@link
 * org.springframework.transaction.IllegalTransactionStateException} imediata, em vez de deixar o
 * evento fazer commit sozinho em silencio.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditoriaRbacService {

    public static final String MOTIVO_UTILIZADOR_ELIMINADO = "utilizador_eliminado";
    public static final String MOTIVO_PROVISIONAMENTO = "provisionamento";

    private static final String ENTIDADE_TIPO_PAPEL = "papel_escritorio";
    private static final String ENTIDADE_TIPO_ATRIBUICAO = "atribuicao_papel";

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    // -----------------------------------------------------------------------------------------
    // Escrita
    // -----------------------------------------------------------------------------------------

    @Transactional(propagation = Propagation.MANDATORY)
    public void registarPapelCriado(UUID tenantId, UserPrincipal autor, TenantRole papel) {
        Map<String, Object> detalhe = new LinkedHashMap<>();
        put(detalhe, "autorNome", nomeDoAutor(autor));
        put(detalhe, "papelId", idComoTexto(papel.getId()));
        put(detalhe, "papelNome", papel.getNome());
        put(detalhe, "permissoesAdicionadas", chavesDePermissao(papel.getPermissions()));
        gravar(tenantId, autor, "papel_criar", ENTIDADE_TIPO_PAPEL, idComoTexto(papel.getId()), detalhe);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registarPapelRenomeado(UUID tenantId, UserPrincipal autor, UUID papelId, String nomeAntigo, String nomeNovo) {
        Map<String, Object> detalhe = new LinkedHashMap<>();
        put(detalhe, "autorNome", nomeDoAutor(autor));
        put(detalhe, "papelId", idComoTexto(papelId));
        put(detalhe, "papelNome", nomeNovo);
        put(detalhe, "nomeAntigo", nomeAntigo);
        put(detalhe, "nomeNovo", nomeNovo);
        gravar(tenantId, autor, "papel_renomear", ENTIDADE_TIPO_PAPEL, idComoTexto(papelId), detalhe);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registarPapelApagado(UUID tenantId, UserPrincipal autor, TenantRole papel) {
        Map<String, Object> detalhe = new LinkedHashMap<>();
        put(detalhe, "autorNome", nomeDoAutor(autor));
        put(detalhe, "papelId", idComoTexto(papel.getId()));
        put(detalhe, "papelNome", papel.getNome());
        put(detalhe, "permissoesRemovidas", chavesDePermissao(papel.getPermissions()));
        gravar(tenantId, autor, "papel_apagar", ENTIDADE_TIPO_PAPEL, idComoTexto(papel.getId()), detalhe);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registarPermissoesAlteradas(UUID tenantId, UserPrincipal autor, TenantRole papel,
                                             Set<String> adicionadas, Set<String> removidas) {
        List<String> add = ordenadaOuVazia(adicionadas);
        List<String> rem = ordenadaOuVazia(removidas);
        if (add.isEmpty() && rem.isEmpty()) {
            // Nada mudou -- nao ha o que registar (o comportamento espera zero saves aqui).
            return;
        }
        Map<String, Object> detalhe = new LinkedHashMap<>();
        put(detalhe, "autorNome", nomeDoAutor(autor));
        put(detalhe, "papelId", idComoTexto(papel.getId()));
        put(detalhe, "papelNome", papel.getNome());
        put(detalhe, "permissoesAdicionadas", add);
        put(detalhe, "permissoesRemovidas", rem);
        gravar(tenantId, autor, "papel_permissoes_alterar", ENTIDADE_TIPO_PAPEL, idComoTexto(papel.getId()), detalhe);
    }

    /**
     * Calcula a diferenca entre {@code antes} e {@code depois} por {@link TenantRole#getId()},
     * nunca por identidade de objeto (duas instancias diferentes do mesmo papel, vindas de
     * consultas separadas, nao podem parecer uma atribuicao/retirada). {@code null} conta como
     * conjunto vazio em ambos os lados. Itera numa ordem deterministica (nome, depois id) para
     * que testes e logs sejam estaveis entre execucoes.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registarAtribuicoes(UUID tenantId, UserPrincipal autor, User alvo,
                                     Set<TenantRole> antes, Set<TenantRole> depois, String motivo) {
        Map<UUID, TenantRole> antesPorId = indexarPorId(antes);
        Map<UUID, TenantRole> depoisPorId = indexarPorId(depois);

        List<TenantRole> adicionados = depoisPorId.values().stream()
                .filter(papel -> !antesPorId.containsKey(papel.getId()))
                .sorted(Comparator.comparing(TenantRole::getNome).thenComparing(TenantRole::getId))
                .collect(Collectors.toList());
        List<TenantRole> removidos = antesPorId.values().stream()
                .filter(papel -> !depoisPorId.containsKey(papel.getId()))
                .sorted(Comparator.comparing(TenantRole::getNome).thenComparing(TenantRole::getId))
                .collect(Collectors.toList());

        for (TenantRole papel : adicionados) {
            gravarAtribuicao(tenantId, autor, alvo, papel, "papel_atribuir", motivo);
        }
        for (TenantRole papel : removidos) {
            gravarAtribuicao(tenantId, autor, alvo, papel, "papel_retirar", motivo);
        }
    }

    private void gravarAtribuicao(UUID tenantId, UserPrincipal autor, User alvo, TenantRole papel,
                                   String acao, String motivo) {
        Map<String, Object> detalhe = new LinkedHashMap<>();
        put(detalhe, "autorNome", nomeDoAutor(autor));
        put(detalhe, "alvoNome", alvo.getNome());
        put(detalhe, "papelId", idComoTexto(papel.getId()));
        put(detalhe, "papelNome", papel.getNome());
        put(detalhe, "motivo", motivo);
        gravar(tenantId, autor, acao, ENTIDADE_TIPO_ATRIBUICAO, alvo.getId().toString(), detalhe);
    }

    private Map<UUID, TenantRole> indexarPorId(Set<TenantRole> papeis) {
        if (papeis == null) {
            return Map.of();
        }
        return papeis.stream().collect(Collectors.toMap(TenantRole::getId, Function.identity(), (a, b) -> a));
    }

    /**
     * Actor: le apenas {@code userId} e {@code nome} do principal, nunca o endereco de correio
     * nem qualquer outro campo pessoal -- Decisao 2 revista (128-CONTEXT.md): {@code detalhe}
     * guarda so o nome de apresentacao, nunca endereco de correio, telefone ou outro dado
     * pessoal. (Este comentario evita deliberadamente escrever o nome literal do getter
     * proibido, para nao acionar falsamente o gate de grep que o Plano 02 usa para provar a sua
     * ausencia do codigo -- ver AuditoriaRbacServiceTest.)
     */
    private String nomeDoAutor(UserPrincipal autor) {
        return autor == null ? null : autor.getNome();
    }

    private List<String> chavesDePermissao(Collection<Permission> permissoes) {
        if (permissoes == null) {
            return List.of();
        }
        return permissoes.stream().map(Permission::getNome).sorted().collect(Collectors.toList());
    }

    private List<String> ordenadaOuVazia(Set<String> valores) {
        if (valores == null) {
            return List.of();
        }
        return valores.stream().sorted().collect(Collectors.toList());
    }

    private String idComoTexto(UUID id) {
        return id == null ? null : id.toString();
    }

    /**
     * Omite valores nulos e colecoes vazias -- o detalhe gravado nunca tem uma chave "adicionada
     * mas vazia", o que manteria o JSON limpo e a leitura (Plan 07/08) simples.
     */
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
     * Cada {@link AuditLog} e construido de raiz com {@link AuditLog.AuditLogBuilder} e nunca
     * transporta um id -- {@code save} insere sempre, nunca actualiza (reforca AUDT-04 por
     * construcao, ao lado de {@code @Immutable} e do {@link AuditLogRepository} estreitado).
     * {@code processoId} nunca e definido aqui: os eventos de RBAC nao pertencem a nenhum
     * processo, e isso mantem-nos fora de {@code GET /processos/{id}/audit}, que filtra por
     * {@code processo_id}.
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
     * Uma falha de serializacao aqui e relancada como {@link IllegalStateException} (nao
     * verificada) de proposito: isto propaga para fora do metodo {@code registar*}, que esta
     * dentro da transacao MANDATORY do chamador, e faz o rollback arrastar tambem a mudanca que
     * o evento descreveria -- uma mudanca nunca faz commit sem o seu evento.
     */
    private String serializarDetalhe(Map<String, Object> detalheCampos) {
        try {
            return objectMapper.writeValueAsString(detalheCampos);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar detalhe do evento de auditoria RBAC", e);
        }
    }

    // -----------------------------------------------------------------------------------------
    // Leitura (Decisao 5)
    // -----------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<AuditoriaRbacEntradaDto> listar(UUID tenantId, UUID utilizadorAlvoId, UUID papelId, Pageable pageable) {
        Page<AuditLog> pagina = auditLogRepository.buscarEventosRbac(
                tenantId, idComoTexto(utilizadorAlvoId), idComoTexto(papelId), pageable);
        return pagina.map(this::paraDto);
    }

    /**
     * Nunca resolve nomes a partir de {@code UserRepository} -- os nomes vem SO do snapshot em
     * {@code detalhe} (Decisao 2 revista). O frontend aplica o fallback quando o nome vem nulo
     * (evento anterior a esta migracao, ou detalhe malformado).
     */
    private AuditoriaRbacEntradaDto paraDto(AuditLog auditLog) {
        AuditoriaRbacEntradaDto.AuditoriaRbacEntradaDtoBuilder builder = AuditoriaRbacEntradaDto.builder()
                .id(auditLog.getId())
                .timestamp(auditLog.getTimestamp())
                .acao(auditLog.getAcao())
                .categoria(ENTIDADE_TIPO_PAPEL.equals(auditLog.getEntidadeTipo()) ? "papel" : "atribuicao")
                .alvoId(ENTIDADE_TIPO_ATRIBUICAO.equals(auditLog.getEntidadeTipo()) ? auditLog.getEntidadeId() : null);

        String detalheJson = auditLog.getDetalhe();
        if (detalheJson == null) {
            return builder.build();
        }

        JsonNode raiz;
        try {
            raiz = objectMapper.readTree(detalheJson);
        } catch (JsonProcessingException e) {
            // T-128-12: nunca lanca -- devolve o DTO so com os campos estruturais e regista um
            // WARN com apenas o id da linha, nunca o conteudo do detalhe malformado.
            log.warn("Falha ao interpretar detalhe do evento de auditoria RBAC id={}", auditLog.getId());
            return builder.build();
        }

        return builder
                .autorNome(textoOuNulo(raiz, "autorNome"))
                .alvoNome(textoOuNulo(raiz, "alvoNome"))
                .papelId(textoOuNulo(raiz, "papelId"))
                .papelNome(textoOuNulo(raiz, "papelNome"))
                .nomeAntigo(textoOuNulo(raiz, "nomeAntigo"))
                .nomeNovo(textoOuNulo(raiz, "nomeNovo"))
                .permissoesAdicionadas(listaOuNula(raiz, "permissoesAdicionadas"))
                .permissoesRemovidas(listaOuNula(raiz, "permissoesRemovidas"))
                .motivo(textoOuNulo(raiz, "motivo"))
                .build();
    }

    private String textoOuNulo(JsonNode raiz, String campo) {
        JsonNode no = raiz.get(campo);
        return (no == null || no.isNull()) ? null : no.asText();
    }

    private List<String> listaOuNula(JsonNode raiz, String campo) {
        JsonNode no = raiz.get(campo);
        if (no == null || !no.isArray()) {
            return null;
        }
        List<String> valores = new ArrayList<>();
        no.forEach(item -> valores.add(item.asText()));
        return valores;
    }
}
