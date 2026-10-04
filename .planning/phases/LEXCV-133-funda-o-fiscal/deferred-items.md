# Deferred items — Phase 133

Found during the 133-08 end-to-end run. Not caused by this plan, so not fixed here.

## 1. Setup wizard fails on a fresh install (pre-existing, Phase 126/128)

- `POST /api/v1/setup/initialize` on an empty database returns `500 {"error":"UnsupportedOperationException"}`.
- Cause: `SetupService.initializeSystem` builds the admin with `.roles(Set.of(adminRole))`, which is an immutable set. `instanciarMoldesEAtribuirAdminFundador` then calls `userRepository.save(adminUser)`, and Hibernate's merge calls `PersistentSet.clear()` on that immutable set (`SetupService.java:237`).
- Effect: a brand-new install cannot finish the first-run wizard. With `SEED_ENABLED=true` the demo seed also never runs on a fresh database, because it requires `initialized=true` and an empty DB at the same time.
- Likely fix: `.roles(new HashSet<>(Set.of(adminRole)))` in `SetupService` (and check the same pattern in `provisionTenant`), plus an IT that runs the wizard against a real database.
- Workaround used for verification: truncate tenant/user tables, insert `system_settings(id=1, is_initialized=true)`, then restart with `SEED_ENABLED=true`.

## 2. DDL error logged on every boot after the first (Plan 133-01)

- With `ddl-auto: update`, every boot after the first logs `WARN ... Error executing DDL "alter table if exists t_serie_fiscal alter column ambiente set data type varchar(32) not null"` with `syntax error at or near "not"`.
- Cause: `SerieFiscal.ambiente` (and `tipoDocumento`) set `columnDefinition = "varchar(32) not null"`. Because these columns go through an `AttributeConverter`, Hibernate reports a type mismatch and puts the whole `columnDefinition` into `SET DATA TYPE`.
- Effect: the error is non-fatal and the schema is already correct, but it is noise in every boot log.
- Likely fix: `columnDefinition = "varchar(32)"` (keep `nullable = false`). Then confirm the parity IT and `validate` mode still pass.
- **Resolved (code review WR-01):** the real cause was a length mismatch (mapped default 255 vs. 32 in the database), not the `not null` text, so `columnDefinition = "varchar(32)"` would still have emitted an ALTER on every boot (and `ConfiguracaoFiscal.regimeIva` was already running a silent one). Fixed by dropping `columnDefinition` and declaring `length = 32` on `tipoDocumento`, `ambiente` and `regimeIva`. `MigracaoFiscal133IT.segundoArranqueEmUpdateNaoEmiteDdlFiscal` runs the `update` migrator against the Hibernate-created schema and asserts that no fiscal DDL is emitted.
