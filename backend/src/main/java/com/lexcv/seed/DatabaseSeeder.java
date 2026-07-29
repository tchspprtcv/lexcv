package com.lexcv.seed;

import com.lexcv.models.*;
import com.lexcv.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Component
@RequiredArgsConstructor
public class DatabaseSeeder implements CommandLineRunner {

        private final TenantRepository tenantRepository;
        private final UserRepository userRepository;
        private final RoleRepository roleRepository;
        private final PermissionRepository permissionRepository;
        private final ClienteRepository clienteRepository;
        private final ContaCorrenteRepository contaCorrenteRepository;
        private final ProcessoRepository processoRepository;
        private final ParteRepository parteRepository;
        private final FaseProcessualRepository faseProcessualRepository;
        private final ProcessoFaseRepository processoFaseRepository;
        private final EventoRepository eventoRepository;
        private final HonorarioRepository honorarioRepository;
        private final PagamentoRepository pagamentoRepository;
        private final SystemSettingRepository systemSettingRepository;
        private final PasswordEncoder passwordEncoder;

        @Value("${app.seed.enabled}")
        private boolean seedEnabled;

        @Override
        public void run(String... args) throws Exception {
                seedRbac();

                // Phase 119 (PROV-01): as tres contagens que protegem o bloco de dados demo tem
                // de ser lidas AQUI, antes de seedTenantPlataforma() inserir qualquer linha -- a
                // tenant reservada e incondicional, logo se fossem lidas depois dela ficariam
                // permanentemente >= 1 e o bloco de dados demo deixaria silenciosamente de correr
                // em qualquer base de dados nova com SEED_ENABLED=true (regressao de ambiente de
                // desenvolvimento, nao um risco de seguranca classico).
                boolean bdVaziaAntesDoSeedPlataforma = tenantRepository.count() == 0
                                && userRepository.count() == 0
                                && clienteRepository.count() == 0;

                Tenant tenantPlataforma = seedTenantPlataforma();

                if (!seedEnabled) {
                        return;
                }

                seedUtilizadorPlataforma(tenantPlataforma);

                boolean initialized = systemSettingRepository.findById(SystemSetting.SINGLETON_ID)
                                .map(SystemSetting::getInitialized)
                                .orElse(false);
                if (!initialized) {
                        return;
                }

                if (!bdVaziaAntesDoSeedPlataforma) {
                        return;
                }

                System.out.println("🌱 Seeding LexCV database...");

                Role adminRole = roleRepository.findByNome("ADMIN").orElseThrow();
                Role assistenteRole = roleRepository.findByNome("ASSISTENTE").orElseThrow();

                // 3. Tenants
                Tenant tenant = Tenant.builder()
                                .nome("Gabinete Jurídico Demonstração")
                                .nif("000000000")
                                .tipoEntidade("PRIVADO")
                                .email("contacto@lexcv.cv")
                                .telefone("+238 200 0000")
                                .build();
                tenant = tenantRepository.save(tenant);
                UUID tenantId = tenant.getId();

                // 4. Users
                User adminUser = User.builder()
                                .tenantId(tenantId)
                                .nome("Administrador (PostgreSQL Real)")
                                .email("admin@lexcv.cv")
                                .passwordHash(passwordEncoder.encode("Pa$$w0rd"))
                                .ativo(true)
                                .roles(Set.of(adminRole))
                                .build();
                userRepository.save(adminUser);

                User assistenteUser = User.builder()
                                .tenantId(tenantId)
                                .nome("Assistente")
                                .email("assistente@lexcv.cv")
                                .passwordHash(passwordEncoder.encode("assist123"))
                                .ativo(true)
                                .roles(Set.of(assistenteRole))
                                .build();
                userRepository.save(assistenteUser);

                // 5. Clientes
                Cliente cliente1 = Cliente.builder()
                                .tenantId(tenantId)
                                .tipo("PARTICULAR")
                                .nome("João Andrade (PostgreSQL Real)")
                                .nif("123456789")
                                .email("joao.andrade@email.com")
                                .telefone("+238 991 1234")
                                .morada("Achada Santo António, Praia")
                                .documentoTipo(DocumentoTipo.BI)
                                .documentoNumero("123456789")
                                .ramoAtividade("Serviços")
                                .detalhesAdicionais("Cliente habitual de consultoria")
                                .build();
                cliente1 = clienteRepository.save(cliente1);
                UUID clienteId1 = cliente1.getId();

                Cliente cliente2 = Cliente.builder()
                                .tenantId(tenantId)
                                .tipo("EMPRESA")
                                .nome("Empresa Atlântico, SA")
                                .nif("512345678")
                                .email("info@atlanticosa.cv")
                                .telefone("+238 231 5678")
                                .morada("Avenida Marginal, Mindelo")
                                .documentoTipo(DocumentoTipo.REG_COMERCIAL)
                                .documentoNumero("512345678")
                                .ramoAtividade("Comércio")
                                .detalhesAdicionais("Empresa líder no setor marítimo")
                                .build();
                cliente2 = clienteRepository.save(cliente2);
                UUID clienteId2 = cliente2.getId();

                // 6. Conta Corrente
                ContaCorrente cc1 = ContaCorrente.builder()
                                .clienteId(clienteId1)
                                .saldo(new BigDecimal("45000.00"))
                                .build();
                contaCorrenteRepository.save(cc1);

                ContaCorrente cc2 = ContaCorrente.builder()
                                .clienteId(clienteId2)
                                .saldo(new BigDecimal("-120000.00"))
                                .build();
                contaCorrenteRepository.save(cc2);

                // 7. Processos
                Processo processo1 = Processo.builder()
                                .tenantId(tenantId)
                                .clienteId(clienteId1)
                                .numeroProcesso("PROC-2026-0001")
                                .tipoProcesso("CÍVEL")
                                .areaJuridica("CONTRATOS")
                                .tribunal("Tribunal de Comarca da Praia")
                                .estado("ATIVO")
                                .dataInicio(LocalDate.of(2026, 5, 12))
                                .descricao("Processo de demonstração para validar endpoints de fases, partes e movimentações.")
                                .build();
                processo1 = processoRepository.save(processo1);
                UUID processoId1 = processo1.getId();

                Processo processo2 = Processo.builder()
                                .tenantId(tenantId)
                                .clienteId(clienteId2)
                                .numeroProcesso("PROC-2026-0002")
                                .tipoProcesso("FISCAL")
                                .areaJuridica("TRIBUTÁRIO")
                                .tribunal("Tribunal Fiscal Aduaneiro")
                                .estado("ENCERRADO")
                                .dataInicio(LocalDate.of(2026, 4, 5))
                                .dataFim(LocalDate.of(2026, 5, 1))
                                .descricao("Processo encerrado para testes de listagens e filtros no frontend.")
                                .build();
                processo2 = processoRepository.save(processo2);
                UUID processoId2 = processo2.getId();

                // 8. Partes
                Parte parte1 = Parte.builder()
                                .processoId(processoId1)
                                .nome("João Andrade")
                                .tipo("AUTOR")
                                .build();
                parteRepository.save(parte1);

                Parte parte2 = Parte.builder()
                                .processoId(processoId1)
                                .nome("Réu Exemplo")
                                .tipo("RÉU")
                                .build();
                parteRepository.save(parte2);

                Parte parte3 = Parte.builder()
                                .processoId(processoId2)
                                .nome("Empresa Atlântico, SA")
                                .tipo("EXECUTADO")
                                .build();
                parteRepository.save(parte3);

                // 9. Fases Processuais (Catalog)
                List<String> fases = Arrays.asList("Distribuição", "Citação", "Contestação", "Audiência", "Sentença");
                Map<String, FaseProcessual> faseMap = new HashMap<>();
                for (String fName : fases) {
                        FaseProcessual f = FaseProcessual.builder().nome(fName).build();
                        f = faseProcessualRepository.save(f);
                        faseMap.put(fName, f);
                }

                // 10. Processo Fases
                ProcessoFase pf1 = ProcessoFase.builder()
                                .processoId(processoId1)
                                .faseId(faseMap.get("Distribuição").getId())
                                .dataInicio(LocalDate.of(2026, 5, 12))
                                .dataFim(LocalDate.of(2026, 5, 12))
                                .ativa(false)
                                .build();
                processoFaseRepository.save(pf1);

                ProcessoFase pf2 = ProcessoFase.builder()
                                .processoId(processoId1)
                                .faseId(faseMap.get("Citação").getId())
                                .dataInicio(LocalDate.of(2026, 5, 13))
                                .ativa(true)
                                .build();
                processoFaseRepository.save(pf2);

                ProcessoFase pf3 = ProcessoFase.builder()
                                .processoId(processoId2)
                                .faseId(faseMap.get("Sentença").getId())
                                .dataInicio(LocalDate.of(2026, 4, 5))
                                .dataFim(LocalDate.of(2026, 5, 1))
                                .ativa(false)
                                .build();
                processoFaseRepository.save(pf3);

                // 11. Eventos (Agenda)
                Evento ev1 = Evento.builder()
                                .tenantId(tenantId)
                                .processoId(processoId1)
                                .tipo("PRAZO")
                                .titulo("Prazo: apresentar documento")
                                .descricao("Submeter os comprovativos físicos de pagamento.")
                                .dataInicio(LocalDateTime.of(2026, 5, 28, 9, 0))
                                .dataFim(LocalDateTime.of(2026, 5, 28, 10, 0))
                                .prioridade("ALTA")
                                .concluido(false)
                                .build();
                eventoRepository.save(ev1);

                Evento ev2 = Evento.builder()
                                .tenantId(tenantId)
                                .processoId(processoId1)
                                .tipo("AUDIENCIA")
                                .titulo("Audiência preliminar")
                                .descricao("Comparecer com as partes e documentos de identificação.")
                                .dataInicio(LocalDateTime.of(2026, 6, 15, 13, 0))
                                .dataFim(LocalDateTime.of(2026, 6, 15, 14, 0))
                                .prioridade("MEDIA")
                                .concluido(false)
                                .build();
                eventoRepository.save(ev2);

                // 12. Honorários
                Honorario h1 = Honorario.builder()
                                .processoId(processoId1)
                                .valorTotal(new BigDecimal("200000.00"))
                                .descricao("Honorários iniciais (exemplo)")
                                .dataAcordo(LocalDate.of(2026, 5, 1))
                                .build();
                h1 = honorarioRepository.save(h1);

                Honorario h2 = Honorario.builder()
                                .processoId(processoId2)
                                .valorTotal(new BigDecimal("500000.00"))
                                .descricao("Honorários finais (exemplo)")
                                .dataAcordo(LocalDate.of(2026, 5, 10))
                                .build();
                h2 = honorarioRepository.save(h2);

                // 13. Pagamentos
                Pagamento pag1 = Pagamento.builder()
                                .honorarioId(h1.getId())
                                .valorPago(new BigDecimal("120000.00"))
                                .dataPagamento(LocalDate.of(2026, 5, 3))
                                .metodo("TRANSFERENCIA")
                                .build();
                pagamentoRepository.save(pag1);

                Pagamento pag2 = Pagamento.builder()
                                .honorarioId(h2.getId())
                                .valorPago(new BigDecimal("380000.00"))
                                .dataPagamento(LocalDate.of(2026, 5, 18))
                                .metodo("DINHEIRO")
                                .build();
                pagamentoRepository.save(pag2);

                System.out.println("✅ LexCV database successfully seeded with default fixtures.");
        }

        private void seedRbac() {
                List<String> permKeys = Arrays.asList(
                                "clientes:view", "clientes:edit",
                                "processos:view", "processos:edit",
                                "processos:create", "processos:manage",
                                "agenda:view", "agenda:edit",
                                "documentos:view", "documentos:edit",
                                "financeiro:view", "financeiro:edit",
                                "rbac:manage", "users:manage",
                                "pareceres:view", "pareceres:create", "pareceres:edit", "pareceres:manage",
                                "notificacoes:view");

                Map<String, Permission> permissionMap = new HashMap<>();
                for (String key : permKeys) {
                        Permission perm = permissionRepository.findByNome(key)
                                        .orElseGet(() -> permissionRepository
                                                        .save(Permission.builder().nome(key).build()));
                        permissionMap.put(key, perm);
                }

                upsertRolePermissions("ADMIN", permissionMap.values());

                upsertRolePermissions("ASSISTENTE", Arrays.asList(
                                permissionMap.get("clientes:view"),
                                permissionMap.get("clientes:edit"),
                                permissionMap.get("processos:view"),
                                permissionMap.get("agenda:view"),
                                permissionMap.get("documentos:view"),
                                permissionMap.get("pareceres:view"),
                                permissionMap.get("notificacoes:view")));

                upsertRolePermissions("TECNICO", Arrays.asList(
                                permissionMap.get("clientes:view"),
                                permissionMap.get("processos:view"),
                                permissionMap.get("agenda:view"),
                                permissionMap.get("agenda:edit"),
                                permissionMap.get("documentos:view"),
                                permissionMap.get("financeiro:view"),
                                permissionMap.get("pareceres:view"),
                                permissionMap.get("notificacoes:view")));

                upsertRolePermissions("ADVOGADO", Arrays.asList(
                                permissionMap.get("clientes:view"),
                                permissionMap.get("clientes:edit"),
                                permissionMap.get("processos:view"),
                                permissionMap.get("processos:edit"),
                                permissionMap.get("processos:create"),
                                permissionMap.get("processos:manage"),
                                permissionMap.get("agenda:view"),
                                permissionMap.get("agenda:edit"),
                                permissionMap.get("documentos:view"),
                                permissionMap.get("documentos:edit"),
                                permissionMap.get("financeiro:view"),
                                permissionMap.get("pareceres:view"),
                                permissionMap.get("pareceres:create"),
                                permissionMap.get("pareceres:edit"),
                                permissionMap.get("notificacoes:view")));

                // Phase 119 (PROV-01): papel de plataforma com coleccao de permissoes
                // deliberadamente VAZIA. Opera exclusivamente atraves do seu proprio endpoint
                // gated por @PreAuthorize("hasRole('PLATAFORMA_ADMIN')") (Plan 04), nunca atraves
                // do sistema RBAC scoped por tenant -- atribuir-lhe qualquer clientes:*/processos:*/
                // rbac:manage/users:manage contradiria a decisao bloqueada em CONTEXT.md.
                upsertRolePermissions("PLATAFORMA_ADMIN", Collections.emptyList());
        }

        private void upsertRolePermissions(String roleName, Collection<Permission> permissions) {
                Role role = roleRepository.findByNome(roleName)
                                .orElseGet(() -> roleRepository.save(Role.builder().nome(roleName).build()));

                boolean changed = role.getPermissions().addAll(permissions);
                if (changed) {
                        roleRepository.save(role);
                }
        }

        /**
         * Tenant reservada de plataforma (Phase 119, PROV-01), seedada INCONDICIONALMENTE em
         * todos os arranques -- e infraestrutura, nao dados demo. A Phase 120 (provisionamento
         * multi-tenant) precisa que esta tenant exista quer haja ou nao seed de demo. Find-or-create
         * idempotente por nome literal; nao cria nenhuma credencial associada.
         *
         * <p>WR-01 (119-REVIEW.md): esta e uma check-then-act classica sem lock nem constraint
         * unique em {@code t_tenant.nome} -- um arranque concorrente de >1 instancia contra a
         * mesma base de dados vazia pode inserir duas linhas "LexCV". Aceite tal e qual,
         * consistente com o padrao ja existente em {@code upsertRolePermissions} para
         * Role/Permission: e um risco de arranque num contexto de deployment tipicamente
         * single-instance, nao uma superficie exposta a utilizadores, e nao vale a pena
         * introduzir locking pesado nem uma migracao de schema manual (este projeto nao tem
         * Flyway/Liquibase) so para este boot-time race. Em vez disso,
         * {@code tenantRepository.findFirstByNome(...)} (em vez de {@code findByNome}, que
         * lancaria {@code IncorrectResultSizeDataAccessException} perante mais de uma linha)
         * garante que uma eventual duplicata nunca transforma esta corrida transitoria num
         * crash-loop permanente em todos os arranques seguintes -- apenas ignora a linha extra e
         * segue em frente.
         */
        private Tenant seedTenantPlataforma() {
                return tenantRepository.findFirstByNome("LexCV")
                                .orElseGet(() -> tenantRepository.save(Tenant.builder().nome("LexCV").build()));
        }

        /**
         * Utilizador bootstrap de administrador de plataforma, deliberadamente atras do gate
         * {@code seedEnabled} -- o mesmo motivo que ja protege o utilizador demo
         * {@code admin@lexcv.cv}: "Pa$$w0rd" e uma password publicamente documentada (CLAUDE.md)
         * e nao pode existir por omissao numa instalacao de producao (mitigacao T-119-06).
         * Find-or-create idempotente por email; o papel PLATAFORMA_ADMIN e propriedade de
         * seedRbac() (Task 1) -- se nao existir, e uma falha de ordem de arranque, nao algo a
         * reparar aqui.
         *
         * <p>WR-01 (119-REVIEW.md): mesma classe de corrida de {@code seedTenantPlataforma()},
         * mas {@code User.email} ja tem uma constraint {@code unique = true} real -- por isso a
         * instancia perdedora de um arranque concorrente falha o seu proprio
         * {@code CommandLineRunner.run()} com {@code DataIntegrityViolationException} nesse
         * arranque especifico, sem nunca chegar a duplicar a linha. Nao e um crash-loop
         * permanente como o caso da tenant: um restart subsequente e nao-concorrente encontra a
         * linha do vencedor e prossegue normalmente. Aceite tal e qual, sem try/catch adicional,
         * pela mesma razao de {@code seedTenantPlataforma()}.
         */
        private void seedUtilizadorPlataforma(Tenant tenantPlataforma) {
                Role plataformaAdminRole = roleRepository.findByNome("PLATAFORMA_ADMIN")
                                .orElseThrow(() -> new IllegalStateException(
                                                "O papel PLATAFORMA_ADMIN nao esta configurado -- seedRbac() tem de correr antes de seedUtilizadorPlataforma()."));

                if (userRepository.findByEmail("plataforma@lexcv.cv").isEmpty()) {
                        User utilizadorPlataforma = User.builder()
                                        .tenantId(tenantPlataforma.getId())
                                        .nome("Administrador de Plataforma")
                                        .email("plataforma@lexcv.cv")
                                        .passwordHash(passwordEncoder.encode("Pa$$w0rd"))
                                        .ativo(true)
                                        .roles(Set.of(plataformaAdminRole))
                                        .build();
                        userRepository.save(utilizadorPlataforma);

                        System.out.println("⚠️  Utilizador de administrador de plataforma criado (plataforma@lexcv.cv) "
                                        + "com password por omissao -- isto so acontece porque app.seed.enabled=true. "
                                        + "Mudar a password antes de qualquer utilizacao fora de desenvolvimento.");
                }
        }
}
