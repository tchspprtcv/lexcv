-- Phase 125 (MOLD-01/MOLD-03): create t_tenant_role + t_tenant_role_permission and add
-- t_role.instanciavel
--
-- IMPORTANT: This is a REQUIRED manual production migration script. It MUST be run manually
-- (e.g. via psql or DBeaver) against the database BEFORE or DURING deploying the code change
-- that adds the `instanciavel` field to the `Role` entity
-- (backend/src/main/java/com/lexcv/models/Role.java) and introduces the new `TenantRole`
-- entity (backend/src/main/java/com/lexcv/models/TenantRole.java).
--
-- Why: `application.yml` runs `ddl-auto: update` and creates this column/these tables by
-- itself on a fresh/dev database from the entity mapping. A database running with
-- `SPRING_JPA_HIBERNATE_DDL_AUTO=validate` refuses to start without them -- `validate` never
-- creates or alters schema, it only checks the existing schema is compatible at startup.
-- There is no automated migration runner in this repository (no Flyway, no Liquibase -- only
-- Hibernate `ddl-auto` for schema evolution). Execution of this script is therefore manual:
-- run it once against each environment's database (staging/prod) before that environment
-- picks up the deploy that introduces these fields.
--
-- Deliberately no constraint tying t_tenant_role.molde_id back to t_role: the provenance a
-- TenantRole row carries is historical, not a live reference (D: "Snapshot, not live
-- reference", 125-CONTEXT.md). A constraint here would make a molde impossible to delete in
-- the future without cascading into every tenant that already instantiated it -- exactly the
-- coupling the snapshot decision exists to avoid. TenantRole.moldeId (the Java field) mirrors
-- this: a bare Integer column, never a navigable @ManyToOne/@OneToOne association.
--
-- Deliberately NO backfill UPDATE for t_role.instanciavel here: this script only creates the
-- column/tables. DatabaseSeeder.seedRbac() runs unconditionally on the very next boot (it is
-- the first statement of CommandLineRunner.run()) and converges instanciavel on all 5 existing
-- t_role rows (true for ADMIN/ASSISTENTE/TECNICO/ADVOGADO, false for PLATAFORMA_ADMIN) in that
-- same boot -- exactly as the `124` script already does for rotulo/descricao/modulo/ordem on
-- t_permission.
--
-- Idempotent: every statement below guards its own creation, so this script is safe to run
-- twice against the same database.

ALTER TABLE t_role ADD COLUMN IF NOT EXISTS instanciavel BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS t_tenant_role (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    nome VARCHAR(255) NOT NULL,
    molde_id INTEGER,
    sistema BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_tenant_role_tenant_nome UNIQUE (tenant_id, nome)
);

CREATE TABLE IF NOT EXISTS t_tenant_role_permission (
    tenant_role_id UUID NOT NULL REFERENCES t_tenant_role(id),
    permission_id INTEGER NOT NULL REFERENCES t_permission(id),
    PRIMARY KEY (tenant_role_id, permission_id)
);

-- Supports TenantRoleRepository.countByMoldeId, called once per molde listed by the
-- plataforma/moldes console to show how many tenants already instantiated it.
CREATE INDEX IF NOT EXISTS idx_tenant_role_molde ON t_tenant_role(molde_id);
