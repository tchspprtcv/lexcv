---
phase: 131-backend-tenant-reservado-lexcv
plan: 01
completed: 2026-09-24
---

# Phase 131 Plan 01 Summary

Renomeado o tenant reservado da plataforma de "ALCv" para "LexCV" em 7 ficheiros de produção e 6
de teste, incluindo o nome do método de teste `migrar_tenantReservadaALCv_...` →
`migrar_tenantReservadaLexCV_...`. Nova migração SQL `131-rename-tenant-reservado-lexcv.sql`
criada e documentada em `backend/migrations/README.md` (agora 18 scripts no total, 10 pendentes em
bases de dados de cliente). `.planning/STATE.md` atualizado com a nova migração pendente.

**Deviations from plan:** Ambiente de execução não tem JDK 23 disponível (só JDK 21, sem acesso de
rede a distribuições de JDK para instalar uma). `mvn test`/`spotbugs:check` correram com
`-Dmaven.compiler.release=21` passado só na linha de comando — `pom.xml` continua a declarar
`<java.version>23</java.version>`, inalterado. Isto é uma limitação do ambiente de verificação
desta sessão, não uma alteração de código; a verificação em CI (que corre com JDK 23 real) não é
afetada.

**Verification commands run:**
- `grep -rn ALCv <13 ficheiros>` — zero ocorrências remanescentes
- `mvn -Dmaven.compiler.release=21 -DskipTests compile` — BUILD SUCCESS
- `mvn -Dmaven.compiler.release=21 -Dtest='AuthControllerTenantSuspensoTest,PlatformAdminControllerTest,PublicControllerTest,DatabaseSeederPlataformaAdminTest,MigracaoPapeisEscritorioServiceTest,SetupServiceInstanciacaoMoldesTest' test` — 57/57 passam
- `mvn -Dmaven.compiler.release=21 -Dtest='*Test' test` (suite completa) — 432/432 passam, 0 falhas, 0 erros
- `mvn -Dmaven.compiler.release=21 spotbugs:check` — exit 0, zero achados
