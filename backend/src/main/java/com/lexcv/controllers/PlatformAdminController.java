package com.lexcv.controllers;

import com.lexcv.dtos.SetupInitializeRequest;
import com.lexcv.dtos.TenantAdminSummaryResponse;
import com.lexcv.dtos.TenantProvisionResponse;
import com.lexcv.dtos.TenantUpdateRequest;
import com.lexcv.models.Tenant;
import com.lexcv.repositories.TenantRepository;
import com.lexcv.repositories.UserRepository;
import com.lexcv.services.SetupService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Superfície de administração de plataforma (Phase 119/120, PROV-01/PROV-03/PROV-04/PROV-05/
 * PROV-06). Gated a {@code PLATAFORMA_ADMIN} ao nível da classe -- nunca acessível a um
 * {@code ADMIN} de um escritório normal (tenant comum); o gate de classe cobre automaticamente
 * qualquer handler acrescentado aqui. A Phase 120 (Plan 02) acrescentou 3 handlers novos --
 * listagem com utilização, ajuste de plano/limite e alternância de suspenso/ativo -- todos
 * cobertos pelo mesmo gate de classe herdado, sem nenhum {@code @PreAuthorize} adicional por
 * método.
 *
 * <p>Ao contrário de {@link AdminController}, este controller NÃO é tenant-scoped: nenhum
 * handler lê o contexto de segurança nem o principal autenticado do chamador -- a criação
 * provisiona um tenant NOVO, e a listagem/ajuste/suspensão operam deliberadamente sobre TODOS os
 * tenants, nunca apenas sobre o tenant de quem chama. Não há {@code getTenantId()} nenhum para
 * ler.
 *
 * <p>{@code POST /api/v1/setup/initialize} ({@link SetupController}) continua a existir,
 * público e com o seu gate singleton intacto; este controller é um caminho totalmente
 * distinto -- nunca lhe chama, nunca reutiliza {@code initializeSystem}, e nunca consulta
 * {@code isInitialized()}.
 */
@RestController
@RequestMapping("/api/v1/platform")
@PreAuthorize("hasRole('PLATAFORMA_ADMIN')")
@RequiredArgsConstructor
public class PlatformAdminController {

    // Phase 120 (Plan 02): nome literal da tenant reservada de plataforma, seedada
    // incondicionalmente por DatabaseSeeder.seedTenantPlataforma() (Phase 119). Usado apenas para
    // recusar a suspensão desta tenant específica em setTenantAtivo -- suspendê-la trancaria fora
    // o único PLATAFORMA_ADMIN existente, sem via de recuperação pela aplicação.
    private static final String TENANT_RESERVADO = "LexCV";

    private final SetupService setupService;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;

    @PostMapping("/tenants")
    public ResponseEntity<?> createTenant(@RequestBody SetupInitializeRequest request) {
        try {
            Tenant tenant = setupService.provisionTenant(request);
            TenantProvisionResponse response = TenantProvisionResponse.builder()
                    .id(tenant.getId())
                    .nome(tenant.getNome())
                    .build();
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", ex.getMessage()));
        } catch (DataIntegrityViolationException ex) {
            // WR-02 (119-REVIEW.md): TOCTOU entre o pre-check findByEmail(...).isPresent() de
            // provisionTenant e o commit real da transacao. User.id usa
            // GenerationType.UUID (gerado em memoria, sem round-trip a BD), pelo que o
            // Hibernate tipicamente adia o INSERT ate ao flush/commit da transacao
            // @Transactional -- ou seja, DEPOIS de provisionTenant ja ter corrido o seu proprio
            // pre-check e devolvido sem erro. O commit acontece dentro desta mesma chamada
            // (fronteira do proxy transacional de SetupService), por isso so este catch aqui --
            // nao um try/catch dentro do proprio metodo do servico -- consegue apanhar esta
            // excecao para o pedido perdedor de uma corrida concorrente com o mesmo adminEmail.
            // Traduzida para a mesma mensagem/400 do caso nao-concorrente (pre-check), para o
            // comportamento visivel ao cliente nao depender de timing.
            return ResponseEntity.badRequest().body(Map.of("message", "Já existe um utilizador com este email."));
        }
    }

    /**
     * {@code GET /api/v1/platform/tenants} (PROV-03): devolve todos os tenants existentes, cada
     * um com o número de utilizadores ativos calculado ao vivo via
     * {@link UserRepository#countByTenantIdAndAtivoTrue(UUID)} -- a única contagem deste tipo em
     * todo o codebase (ver o doc-comment do próprio método). Deliberadamente sem filtro de tenant:
     * esta listagem é cross-tenant por desenho, e o gate de classe
     * {@code hasRole('PLATAFORMA_ADMIN')} é a única fronteira de autorização que decide quem a
     * pode alcançar. A lista é ordenada por nome (case-insensitive) para a consola ter uma ordem
     * estável entre chamadas -- {@code findAll()} não garante nenhuma ordem própria.
     */
    @GetMapping("/tenants")
    public ResponseEntity<?> listTenants() {
        List<TenantAdminSummaryResponse> tenants = tenantRepository.findAll().stream()
                .map(this::toSummary)
                .sorted(Comparator.comparing(TenantAdminSummaryResponse::getNome, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
        return ResponseEntity.ok(tenants);
    }

    /**
     * {@code PUT /api/v1/platform/tenants/{id}} (PROV-04): ajusta {@code plano} e
     * {@code limiteUtilizadores} de um tenant já existente. {@code plano} é obrigatório -- um
     * corpo que o omita desserializa para {@code null} e é recusado aqui (um valor de enum
     * inválido já é recusado antes disso, pelo Jackson, traduzido em {@code 400} pelo handler de
     * {@code HttpMessageNotReadableException} acrescentado por esta mesma fase em
     * {@code GlobalExceptionHandler}). {@code limiteUtilizadores} continua a aceitar {@code null}
     * como "sem limite"; um valor presente tem de ser maior ou igual a 1. Nunca toca em
     * {@code ativo} -- essa alternância tem o seu próprio endpoint dedicado, ver
     * {@link #setTenantAtivo(UUID, Map)}.
     */
    @PutMapping("/tenants/{id}")
    public ResponseEntity<?> updateTenant(@PathVariable UUID id, @RequestBody TenantUpdateRequest request) {
        Tenant tenant = tenantRepository.findById(id).orElse(null);
        if (tenant == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Tenant não encontrado."));
        }

        if (request.getPlano() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "O plano é obrigatório."));
        }

        if (request.getLimiteUtilizadores() != null && request.getLimiteUtilizadores() < 1) {
            return ResponseEntity.badRequest().body(Map.of("message",
                    "O limite de utilizadores deve ser um número inteiro maior ou igual a 1, ou ficar vazio para não aplicar limite."));
        }

        tenant.setPlano(request.getPlano());
        tenant.setLimiteUtilizadores(request.getLimiteUtilizadores());
        Tenant tenantGravado = tenantRepository.save(tenant);

        return ResponseEntity.ok(toSummary(tenantGravado));
    }

    /**
     * {@code PATCH /api/v1/platform/tenants/{id}/ativo} (PROV-05): alterna o estado
     * suspenso/ativo de um tenant. Esta é a camada autoritativa -- a desativação do botão
     * equivalente no frontend é apenas espelho de UX, não a fronteira de segurança real (ver
     * {@code JwtAuthenticationFilter}, que já recusa todos os pedidos autenticados de um tenant
     * suspenso, mesmo em sessões já ativas).
     *
     * <p><b>Guarda da tenant reservada:</b> suspender a tenant reservada (constante
     * {@code TENANT_RESERVADO}, hoje com o nome {@code LexCV}) trancaria fora o único
     * {@code PLATAFORMA_ADMIN} existente, sem nenhuma via de recuperação pela aplicação -- só por
     * SQL manual. A guarda usa comparação literal de {@code nome} porque {@code t_tenant.nome}
     * não tem constraint {@code unique} (ver {@code TenantRepository#findFirstByNome}, WR-01 da
     * Phase 119): um segundo tenant com o mesmo nome ficaria também não-suspensível, o que é o
     * lado seguro do erro. A guarda só bloqueia a suspensão -- reativar uma tenant reservada é
     * inofensivo e nunca falha.
     */
    @PatchMapping("/tenants/{id}/ativo")
    public ResponseEntity<?> setTenantAtivo(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        if (!(body.get("ativo") instanceof Boolean novoAtivo)) {
            return ResponseEntity.badRequest().body(Map.of("message", "O campo ativo deve ser um valor booleano."));
        }

        Tenant tenant = tenantRepository.findById(id).orElse(null);
        if (tenant == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Tenant não encontrado."));
        }

        if (TENANT_RESERVADO.equals(tenant.getNome()) && !novoAtivo) {
            return ResponseEntity.badRequest().body(Map.of("message", "Não é possível suspender o tenant da plataforma (LexCV)."));
        }

        tenant.setAtivo(novoAtivo);
        Tenant tenantGravado = tenantRepository.save(tenant);

        return ResponseEntity.ok(toSummary(tenantGravado));
    }

    /**
     * Projeta um {@link Tenant} para o contrato de resposta partilhado pelos 3 handlers acima --
     * ver o doc-comment de {@link TenantAdminSummaryResponse} para o que fica deliberadamente de
     * fora.
     */
    private TenantAdminSummaryResponse toSummary(Tenant tenant) {
        return TenantAdminSummaryResponse.builder()
                .id(tenant.getId())
                .nome(tenant.getNome())
                .plano(tenant.getPlano())
                .limiteUtilizadores(tenant.getLimiteUtilizadores())
                .ativo(tenant.getAtivo())
                .utilizadoresAtivos(userRepository.countByTenantIdAndAtivoTrue(tenant.getId()))
                .build();
    }
}
