package com.lexcv.services;

import com.lexcv.models.Tenant;
import com.lexcv.models.TenantRole;
import com.lexcv.models.User;
import com.lexcv.repositories.TenantRepository;
import com.lexcv.repositories.TenantRoleRepository;
import com.lexcv.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Phase 126 Plan 03 (MIGR-01/MIGR-02): conversão convergente por tenant de todo o escritório já
 * existente -- instancia os moldes actuais em falta e associa cada utilizador ao {@link
 * TenantRole} equivalente ao seu papel global, com a verificação de deriva zero a correr dentro
 * da MESMA transacção.
 *
 * <p>Duas propriedades tornam esta conversão segura e nenhuma delas é opcional: (a) reutiliza o
 * mecanismo de {@link SetupService}, já provado na Phase 125, para instanciar moldes em vez de o
 * reimplementar numa segunda definição capaz de divergir; (b) {@link
 * VerificacaoDerivaPapeisService#verificarSemDeriva} corre no fim da mesma transacção -- um
 * aborto rebobina tenants, papéis instanciados e associações juntos, nunca deixa dados
 * meio-convertidos.
 *
 * <p><b>Convergente, não one-shot:</b> correr {@link #migrar()} duas vezes não produz nenhuma
 * escrita na segunda passagem -- a guarda de {@code t_tenant_role} vazio evita re-instanciar
 * moldes, e a comparação {@code alvo.equals(user.getTenantRoles())} evita gravar utilizadores já
 * convertidos.
 *
 * <p><b>{@code t_user_role} nunca é tocado</b> (126-CONTEXT.md, Decisão 4 -- reversibilidade): a
 * colecção de papéis globais do utilizador nunca é reatribuída, esvaziada nem alterada, e
 * nenhuma linha de utilizador ou de papel é apagada em nenhum ponto deste ficheiro.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MigracaoPapeisEscritorioService {

    // Mesmo precedente e mesma razão de PlatformAdminController:75 -- a tenant reservada de
    // plataforma nunca recebe papéis de escritório. Declarada aqui em vez de importada porque a
    // constante de PlatformAdminController é private. plataforma@lexcv.cv tem PLATAFORMA_ADMIN
    // global, que não é instanciável; se esta migração lhe atribuísse papéis de escritório, o
    // resolvedor passaria a ler o lado de escritório e ele perderia toda a autoridade de
    // plataforma no instante do arranque seguinte (126-CONTEXT.md, Decisão 3).
    private static final String TENANT_RESERVADO = "LexCV";

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final TenantRoleRepository tenantRoleRepository;
    private final ResolucaoPapeisService resolucaoPapeisService;
    private final VerificacaoDerivaPapeisService verificacaoDerivaPapeisService;
    private final SetupService setupService;

    /**
     * A transacção é o que torna esta conversão segura: uma divergência detectada no fim rebobina
     * tudo, e um operador nunca fica com metade dos utilizadores convertidos.
     */
    @Transactional
    public void migrar() {
        Map<UUID, VerificacaoDerivaPapeisService.EstadoEfectivo> antes = new HashMap<>();
        List<User> todosOsUtilizadores = new ArrayList<>();

        int tenantsPercorridos = 0;
        int tenantsSaltados = 0;
        int utilizadoresConvertidos = 0;
        int utilizadoresJaEmDia = 0;

        List<Tenant> tenants = tenantRepository.findAll();
        for (Tenant tenant : tenants) {
            if (TENANT_RESERVADO.equals(tenant.getNome())) {
                tenantsSaltados++;
                log.info("Tenant reservada '{}' (id={}) saltada -- nao recebe papeis de escritorio "
                        + "por construcao, nao por remendo.", TENANT_RESERVADO, tenant.getId());
                continue;
            }

            tenantsPercorridos++;
            UUID tenantId = tenant.getId();

            List<User> utilizadoresDoTenant = userRepository.findByTenantId(tenantId);

            // Capturar ANTES de qualquer escrita -- capturar depois de mutar mediria o depois
            // duas vezes.
            antes.putAll(verificacaoDerivaPapeisService.capturarAntes(utilizadoresDoTenant));

            if (tenantRoleRepository.findByTenantId(tenantId).isEmpty()) {
                setupService.instanciarMoldes(tenantId);
            }

            for (User user : utilizadoresDoTenant) {
                Set<TenantRole> alvo;
                try {
                    alvo = resolucaoPapeisService.resolverPapeisDeEscritorio(tenantId, user.getRoles());
                } catch (MapeamentoParcialPapeisException e) {
                    // CR-01/WR-04 (126-REVIEW.md): correspondencia PARCIAL para este utilizador --
                    // alguns dos seus papeis globais tem TenantRole homonimo neste tenant, outros
                    // nao (ex.: um molde tornado instanciavel DEPOIS de este tenant ja ter sido
                    // convertido). Ao contrario do aborto por deriva (verificarSemDeriva, mais
                    // abaixo), NAO se aborta a transaccao inteira por um unico utilizador com
                    // catalogo desactualizado -- fica sem papeis de escritorio, registado aqui, e
                    // continua a resolver por papeis globais, exactamente a mesma postura de
                    // seguranca do ramo "sem correspondencia" logo a seguir. O que muda e que
                    // NUNCA se grava o subconjunto parcial (o que nao seria aceitavel, ver CR-01).
                    log.warn("Utilizador {} tem correspondencia PARCIAL de papel de escritorio no "
                            + "tenant {} -- papeis sem TenantRole homonimo: {}. Fica sem papeis de "
                            + "escritorio (nunca grava um subconjunto parcial) e continua a "
                            + "resolver por papeis globais ate o catalogo de moldes deste tenant "
                            + "ser actualizado.", user.getEmail(), tenantId, e.getPapeisSemCorrespondencia());
                    continue;
                }

                if (alvo.isEmpty()) {
                    log.warn("Utilizador {} sem equivalente de papel de escritorio no tenant {} para os "
                            + "papeis globais {} -- fica sem papeis de escritorio e continua a resolver "
                            + "por papeis globais.", user.getEmail(), tenantId, nomesDosPapeis(user));
                    continue;
                }

                if (alvo.equals(user.getTenantRoles())) {
                    utilizadoresJaEmDia++;
                    continue;
                }

                user.setTenantRoles(new HashSet<>(alvo));
                userRepository.save(user);
                utilizadoresConvertidos++;
            }

            todosOsUtilizadores.addAll(utilizadoresDoTenant);
        }

        // Ainda dentro da transaccao -- se lancar, a transaccao rebobina.
        verificacaoDerivaPapeisService.verificarSemDeriva(antes, todosOsUtilizadores);

        log.info("Conversao de papeis de escritorio concluida -- tenants percorridos: {}, tenants "
                + "saltados: {}, utilizadores convertidos: {}, utilizadores ja em dia: {}.",
                tenantsPercorridos, tenantsSaltados, utilizadoresConvertidos, utilizadoresJaEmDia);
    }

    private Set<String> nomesDosPapeis(User user) {
        Set<String> nomes = new HashSet<>();
        user.getRoles().forEach(role -> nomes.add(role.getNome()));
        return nomes;
    }
}
