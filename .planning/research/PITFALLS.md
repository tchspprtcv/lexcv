# Domain Pitfalls

**Domain:** Adding fields/entities/workflow side-effects to an existing "processos" module (Spring Boot + Next.js, multi-tenant legal practice management)
**Researched:** 2026-07-07
**Scope:** v2.9 Melhoria Módulo Processos — Juízo field, origem enum, Decisão/Facto/Testemunha child entities, Documentos tab, auto-Honorario on formalizar + Termo de Honorários print

## Confidence note

All findings below are HIGH confidence — they are derived directly from reading the actual code in this repository (`ResourceController.java`, `Processo.java`, `Honorario.java`, `Documento.java`, `Cliente.java`, `web/src/hooks/use-processos.ts`, `web/src/app/(dashboard)/processos/novo/page.tsx`, `web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx`), not from generic web research. Line numbers cited are current as of 2026-07-07 and will drift as the codebase changes — treat them as pointers, not permanent anchors.

## Critical Pitfalls

### Pitfall 1: "origem" forgotten in the manual camelCase/snake_case translation layer (repeat of the project's #1 recurring bug)

**What goes wrong:** `origem` is added to the `Processo` JPA entity and to the intake form UI, everything compiles and `pnpm build`/`mvn package` pass, but the value never reaches the database — or reaches the DB but never displays in the UI.

**Why it happens:** This project does not use a global camelCase↔snake_case strategy (deliberately, per `Key Decisions` in PROJECT.md: `@JsonProperty` cirúrgico por campo). Instead, `web/src/hooks/use-processos.ts` hand-maintains two conversion functions:
- `normalizeProcesso()` (API response → UI `Processo` type) — reads `api.origem` and must be told to.
- `toProcessoApiPayload()` (UI form values → API request body) — must explicitly add `origem: payload.origem` to the returned object.

Both are plain object literals with **no type-level requirement that every backend field be mapped**. `ProcessoApi`/`ProcessoApiPayload` are loosely-typed intermediate shapes — omitting a field is not a type error, `tsc` will not catch it, and neither will `pnpm build`. This exact class of bug already happened 3 times in this project (v2.4 Fase migration, v2.8/Phase 79 `cliente_id`/`clienteId` on document upload, and this session's `fase_id`/`nome` mismatch).

**Consequences:** Silent data loss — "origem" gets typed into the form, submit succeeds (200/201), but the value is `null` forever, or an already-saved value never renders on the ficha. No error is thrown anywhere in the stack.

**Prevention:**
1. When adding `juizo`/`origem` to `Processo.java`, in the same commit/PR grep `use-processos.ts` for every existing field name (`tipoProcesso`, `areaJuridica`, etc.) as a checklist and add the new field to **both** `normalizeProcesso()` and `toProcessoApiPayload()`, plus the `ProcessoApi`/`ProcessoApiPayload`/`ProcessoCreateRequest`/`ProcessoUpdateRequest`/`Processo` type definitions in `web/src/types/processos.ts`.
2. After wiring, do a manual round-trip test: create a processo with `origem` set via the UI, reload the page (hard refresh, not client cache), and confirm the value survives. Do not trust `tsc`/build success as evidence.
3. Consider (as a process-level guardrail, not required this milestone) a small integration test that POSTs an intake payload with all fields set and asserts the GET response echoes every one of them — this is the only mechanical way to catch this class of bug given the current architecture.

**Detection:** Field present in DB via direct query but absent from `GET /processos/{id}` JSON response (backend-side gap), or present in JSON but not rendered on the processo detail page (frontend mapping gap). Check both independently.

**Owner phase:** Backend-entity phase (add field + `@Column`) AND frontend-integration phase (wire `use-processos.ts` + form + display) — this is two separate, easy-to-desync changesets. Roadmap should make the frontend mapping an explicit, separately-reviewed task, not an assumed side-effect of "add the field to the entity."

---

### Pitfall 2: "origem" enforced in the frontend wizard step-1 Zod schema but not in backend's `CAMPOS_MINIMOS_POR_TIPO` (or vice versa) — the two validation layers already disagree today

**What goes wrong:** `origem` is required at intake per the milestone spec, but ends up enforced in only one of two independent validation layers, so a processo can be formalized (or even just intake-created) without it depending on which path is taken.

**Why it happens:** This codebase already has **two parallel, hand-written minimum-field-validation systems** for processo creation that do not share a definition:
- **Frontend, step 1 (Intake):** `web/src/schemas/processos.ts` → `processoFormSchema`. Today `tipo_processo`, `area_juridica`, `tribunal`, `numero` are all `optionalTrimmedString` — i.e., the frontend wizard's step-1 Zod schema does **not** currently enforce the same "required" fields the backend enforces later. Only `cliente_id` is required client-side.
- **Backend, formalizar step (not intake):** `ResourceController.java` lines ~72-80, `CAMPOS_MINIMOS_POR_TIPO` — a `Map<String, List<String>>` keyed by `tipo_processo`, checked only inside `formalizarProcesso()` (line ~1181), i.e., at step 3, not at intake (`POST /processos/intake`, line ~1023, does zero field validation beyond forcing `estado=TRIAGEM`).

So today, a processo can already be intake-created with almost nothing filled in, and the user only discovers missing fields when formalizing (step 3), which is confusing but not a security hole. Adding `origem` risks landing in only one of these two places:
- If added only to the frontend Zod schema: a processo created by direct API call (or if the frontend validation is bypassed/has a bug) can skip origem entirely and reach ATIVO state without it, because the backend's `formalizarProcesso` field-check is the only one that actually gates the state transition, and if `origem` isn't added there too, it's not enforced at all server-side.
- If added only to `CAMPOS_MINIMOS_POR_TIPO`: the user fills out intake without being told origem is required, then hits an opaque 422 with `camposEmFalta` at step 3 (a jarring UX regression, and note this map is per-`tipo_processo`, so it must be added to **every** entry, including `"default"` — forgetting one entry means that one tipo_processo silently doesn't require origem).
- The task description explicitly says "required at intake" — but the *only* backend enforcement point that currently exists for minimum fields is `formalizar` (step 3), not `POST /processos/intake` (step 1). If "required at intake" is taken literally, `createProcessoIntake()` itself needs a new check that does not exist for ANY field today (not even `clienteId`) — this is new backend logic, not an extension of an existing pattern.

**Consequences:** Either a compliance gap (origem-less processo reaches ATIVO), or a UX regression (user fills 3 steps then gets blocked with no earlier warning), or both, depending on which layer is missed.

**Prevention:**
1. Decide explicitly, in the roadmap/plan, WHERE origem is enforced and make sure it's ALL of: (a) `POST /processos/intake` backend validation (new — doesn't exist for any field today, so this is genuinely new code, not a copy-paste), (b) `CAMPOS_MINIMOS_POR_TIPO` for every `tipo_processo` key including `"default"` (defense in depth for formalizar, in case intake validation is ever bypassed), (c) frontend `processoFormSchema` step-1 Zod schema (so the user is told at step 1, not step 3).
2. Write the origem check once as a small enum-membership validator reused in both intake creation and formalizar, rather than copy-pasting the same `Set.of("PETICAO_INICIAL", "NOTIFICACOES_AVULSAS")` check into two Java methods that could later drift.
3. Explicitly test: create intake with `origem` omitted via a raw `curl`/Postman call bypassing the frontend wizard, confirm it's rejected server-side (proves layer (a) exists and isn't just a frontend nicety).

**Detection:** Grep `CAMPOS_MINIMOS_POR_TIPO` for `"origem"` after implementation — confirm it's in every map entry, not just `civel`. Separately, confirm `createProcessoIntake()` itself now validates something (today it validates nothing beyond forcing `estado`).

**Owner phase:** Backend-endpoint phase must own both `intake` and `formalizar` validation; a distinct frontend-integration phase should wire the step-1 Zod `.enum()` (not `optionalTrimmedString`) and the step indicator/error messaging — do not let one phase silently assume the other covers it.

---

### Pitfall 3: New child entities (Decisão/Facto/Testemunha) copy the Parte/Fase/Movimentação tenant-check pattern correctly for reads/creates, but skip the update/delete ownership re-check that Fase already had to add

**What goes wrong:** Looking at the three existing "processo child entity" precedents in `ResourceController.java`:
- `Parte` (lines ~1521-1539): has `GET`/`POST` only, both check `processo.getTenantId().equals(getTenantId())` before touching the parte. No `PUT`/`DELETE` exists yet, so there's no precedent for the harder case.
- `ProcessoFase` (lines ~1542-1621): has `GET`/`POST`/`PUT`. The `PUT` (`updateProcessoFase`, line ~1596) does the tenant check on the **parent processo** AND then a **second check**: `pf.getProcessoId().equals(id)` — i.e., it re-verifies the fetched `ProcessoFase` row actually belongs to the `{id}` processo in the URL, not just to *some* processo in the tenant. This is the correct, harder pattern.
- `Movimentacao` (lines ~1624-1643): has `GET`/`POST` only, same single-check pattern as Parte.

None of these three entities have a dedicated tenant_id column — they're tenant-scoped **only transitively** through their parent `Processo`. This is a subtle, easy-to-miss design: a naive port of the `GET`/`POST` pattern to Decisão/Facto/Testemunha (child entities #3, #4, #5) is safe for `GET`/`POST`, but if `PUT`/`DELETE` are added (very likely needed — Decisões get corrected, Testemunhas get removed, Factos get reordered) and someone copies the *simpler* Parte/Movimentacao pattern instead of the *correct* Fase pattern, you get an authorization gap: **checking `processo.tenantId == currentTenant` alone is not enough for `PUT`/`DELETE {childId}` — you must also check the fetched child row's `processoId` equals the `{id}` path segment**, otherwise a user in Tenant A who knows/guesses a Decisão UUID belonging to a *different processo in the same tenant* (or worse, a cross-processo IDOR if the child ID space isn't tenant-partitioned) could update/delete a record on a processo they don't have the URL for. Since IDs here are sequential `Integer` (like `ProcessoFase`'s `faseId: Integer`) rather than `UUID`, this is a realistic guessable-ID scenario if Decisão/Facto/Testemunha reuse `GenerationType.IDENTITY` — check what Honorario used (`Integer id`, `GenerationType.IDENTITY`) versus what fully-UUID entities used.

**Consequences:** Cross-processo (same-tenant) IDOR on update/delete of Decisão/Facto/Testemunha — a real authorization bug, not just a UX issue, especially concerning for Decisão given it can carry an attached court document.

**Prevention:**
1. For every `PUT`/`DELETE {id}/decisoes/{decisaoId}` (and facto/testemunha equivalents), copy the **Fase** pattern exactly: fetch parent processo + tenant check, THEN fetch the child row + `child.getProcessoId().equals(id)` check, not just the Parte/Movimentacao single-check pattern.
2. If Decisão/Facto/Testemunha use `Integer`/sequential IDs (matching the `ProcessoFase`/`Honorario` precedent), the double-check above is **mandatory**, not optional — sequential IDs are trivially enumerable.
3. Explicitly write this ownership check into the phase's acceptance criteria / review checklist rather than assuming "we did tenant checks like the other entities" is sufficient — the *existing* codebase already has an inconsistency (Parte/Movimentacao lack it, Fase has it) that a future audit already flagged as a category of risk in this project's history (`@PreAuthorize` scope mismatches, IDOR-adjacent bugs).

**Detection:** Code review checklist per new child-entity endpoint: does every `PUT`/`DELETE`/single-item `GET` verify BOTH (a) parent processo belongs to tenant AND (b) fetched child row's `processoId` equals the path's `{id}`? If an endpoint only does (a), flag it.

**Owner phase:** Backend-entity phase for Decisão/Facto/Testemunha — put this check explicitly in the phase's plan/task list per entity (3x), not as a single shared assumption. This is exactly the kind of thing a plan-checker/verifier gate should look for given the project's `config.json`-driven ASVS-level-1 enforcement.

---

### Pitfall 4: Auto-created Honorario on formalizar is not idempotent — retrying/double-clicking "Formalizar" or a network retry creates duplicate honorarios

**What goes wrong:** `formalizarProcesso()` (line ~1181) is `@Transactional` and currently ends with `processo.setEstado("ATIVO"); return ResponseEntity.ok(processoRepository.save(processo));`. If Honorario auto-creation is added inside this method, the natural implementation is "if estado transitions to ATIVO, also create a Honorario row." But look at the method's own guard: `if (!"TRIAGEM".equalsIgnoreCase(processo.getEstado())) return 409 CONFLICT`. This guard *does* prevent a second full formalizar call after the first succeeds (since estado is now ATIVO) — **but only if the first call actually completed and committed**. The real risk windows are:
1. **Frontend double-submit before the first request completes:** `onFormalizar()` in `web/src/app/(dashboard)/processos/novo/page.tsx` (line ~164) does call `formalizarProcesso.mutateAsync()` guarded by `formalizarProcesso.isPending` disabling the button — this mitigates the UI-level double click reasonably well today, so the higher risk is (2).
2. **Client retry after a timeout/network error where the server actually succeeded** (classic "did my write happen?" ambiguity) — the frontend's `catch` block treats any thrown error as "formalizar failed, let user retry," but if the backend's transaction actually committed (estado=ATIVO, Honorario created) and only the *response* was lost, a retry will hit the `409 CONFLICT` (state guard) — but only if the Honorario-creation code is placed correctly relative to the state check. If Honorario creation is accidentally placed in a code path that can be reached from `ATIVO` state too (e.g., a separate "generate honorario" trigger added for flexibility, or if formalizar is refactored to be re-callable), duplicates become possible.
3. Even without a bug, the **state-based guard is not the same as idempotency at the Honorario level** — nothing about `formalizarProcesso` checks "does this processo already have an auto-created Honorario" before creating one. Today's `createHonorario()` (line ~2186) endpoint has no uniqueness constraint on `processo_id` (unlike, e.g., the tenant-scoped unique `documento_numero` constraint mentioned in CLAUDE.md) — nothing in the DB schema prevents two Honorario rows for the same processo.

**Consequences:** Duplicate financial records tied to a real processo — a duplicate Honorario would double-count in the Financeiro module, corrupt `ContaCorrente` balance calculations on payment (see `createPagamento`'s balance-update logic at line ~2222), and require manual cleanup, which is much worse for a financial record than for e.g. a duplicate Movimentacao entry.

**Prevention:**
1. Do not rely solely on the `estado != TRIAGEM → 409` guard as the idempotency mechanism. Explicitly check "does an Honorario already exist for this `processo_id`" (`honorarioRepository.findByProcessoId(id)`, which already exists per the `listHonorarios` usage) immediately before creating one inside `formalizarProcesso`, and skip creation (not error) if one already exists — this makes the whole operation naturally idempotent even under retry/replay, independent of the state guard.
2. Consider a DB-level unique constraint or at minimum a code-level invariant comment stating "one auto-created Honorario per processo" — even if the milestone doesn't require blocking manual creation of *additional* honorarios later (e.g., amendments), the *auto-created* one specifically should be a singleton per processo, distinguishable perhaps via `descricao` convention (e.g., seeded from `honorariosPropostos`) or a boolean/source flag if the schema allows a quick addition.
3. Write a specific test: call `formalizar` twice in sequence (simulating retry) and assert exactly one Honorario row exists for the processo afterward, regardless of whether the second call is rejected by the state guard or short-circuited by the existence check.

**Detection:** Query `SELECT processo_id, COUNT(*) FROM t_honorario GROUP BY processo_id HAVING COUNT(*) > 1` after any retry-testing session.

**Owner phase:** Backend-endpoint phase (the `formalizar` handler itself) — this must be an explicit line item in that phase's plan, not an assumed side-effect of "call honorarioRepository.save() at the end of formalizar."

---

### Pitfall 5: Auto-created Honorario silently defaults `valorTotal` to a real (wrong) currency amount instead of requiring explicit confirmation

**What goes wrong:** `Cliente.honorariosPropostos` (a JSON-converted field from the v2.4 intake flow, `Cliente.java` line ~91-94, type `HonorariosPropostos`) already stores a *proposed* fee captured at client intake — totality, "por extenso" (written-out amount), and "previsão" (forecast/estimate). This is the obvious, tempting source to auto-populate the new Honorario's `valorTotal` on formalizar. But:
1. `honorariosPropostos` is captured per-**cliente**, not per-**processo** — a cliente can have multiple processos (the ficha's own "Processos" tab, wired in Phase 77, proves this 1-to-many relationship is real and already in production use). A cliente with 3 active processos has exactly one `honorariosPropostos` value on their record; blindly copying it to Honorario on *every* processo's formalizar would give every processo the same fee, which is almost certainly wrong (each processo may have its own separately-negotiated fee, or none at all if this cliente's proposed value was for a different case entirely).
2. `honorariosPropostos` was captured as a **proposal at intake time**, worded loosely ("por extenso" suggests it was designed to mirror a hand-written estimate on a paper intake form, not a binding contractual value) — auto-materializing it as a real `t_honorario.valor_total` row without any human review turns a soft estimate into a hard financial record with zero confirmation step. `Honorario.valorTotal` is a real `BigDecimal` that flows directly into `ContaCorrente` balance math via `Pagamento` — this is not a cosmetic field.
3. The task description itself flags this risk explicitly ("valor should never be auto-set to a real currency amount without user confirmation") — this is the single highest-severity pitfall in this entire feature set because it's a money bug, not a display bug.

**Consequences:** A processo goes ATIVO with an auto-created Honorario carrying an incorrect, stale, or cliente-level-not-processo-level fee amount, with no visible flag that it needs review, and it can silently start accruing payments against the wrong number before anyone notices — very costly to unwind in a legal billing context (client trust, financial audit trail).

**Prevention:**
1. Do NOT copy any numeric value from `Cliente.honorariosPropostos` into `Honorario.valorTotal` automatically. If the milestone wants to surface the proposed value for convenience, pre-fill it **only in a UI form the user must explicitly submit/confirm** (e.g., a "Confirmar Honorário" step shown right after formalizar succeeds, pre-populated but editable, with `valorTotal` starting `null`/blank until the user saves) — never have the backend `formalizarProcesso()` transaction itself write a non-null `valorTotal`.
2. The backend-created Honorario stub (if auto-created inside `formalizar` for the sake of having a `processo_id` linkage/row to attach the "Termo de Honorários" to) should have `valorTotal = null` and `descricao` indicating "a confirmar" (pending), never a currency figure — this matches the existing `updateHonorario` PATCH-style endpoint (line ~2253) which already supports setting `valorTotal` after the fact via a separate authenticated action gated by `financeiro:edit`.
3. Make the "Termo de Honorários" generation explicitly check for `valorTotal == null` and either block printing or clearly render "___________" (the existing `BLANK` placeholder pattern from `ficha/page.tsx`'s `fmt()` helper) rather than printing a legal document with a blank/zero amount that could be mistaken for "free of charge."
4. RBAC: creating/confirming the real `valorTotal` should require `financeiro:edit` (matching the existing `createHonorario`/`updateHonorario` scope), which is a **different** permission scope than `processos:manage` (which gates `formalizar` itself) — a user with `processos:manage` but not `financeiro:edit` should be able to formalize the processo but should NOT be the one silently setting a real fee amount as a side effect of an action gated under a different permission. This is a second RBAC-scope pitfall layered on top of the money pitfall: the side-effect must not grant financial-write capability through a non-financial permission gate.

**Detection:** Manual test: formalize a processo for a cliente that has `honorariosPropostos` filled in, and confirm the resulting Honorario is NOT pre-filled with that number without an explicit save action by a `financeiro:edit`-scoped user.

**Owner phase:** Backend-endpoint phase must decide and implement the null-valorTotal-stub approach; a distinct frontend-integration phase must build the explicit confirmation UI. Flag this pairing explicitly in the roadmap — do not let "auto-create Honorario" be scoped as a single backend-only task, since the money-safety property depends on the frontend confirmation step existing.

## Moderate Pitfalls

### Pitfall 6: "Termo de Honorários" print template pulls from 3 entities (Cliente, Processo, Honorario) fetched via 3 separate hooks with independent loading/error states — stale or partial data can print silently

**What goes wrong:** The existing print precedent (`ficha/page.tsx`) fetches from multiple hooks (`useCliente`, `useClienteAdvogados`, `useClienteAdministrativos`) and only gates the whole page on `cliente.isLoading`/`cliente.isError` — the secondary hooks' loading/error states are not checked before rendering, they just render empty/whatever-they-have. For "Termo de Honorários," the equivalent risk is worse because it spans 3 *different* top-level entities (Cliente, Processo, Honorario) rather than one entity plus its sub-resources, and one of those (Honorario) may not exist yet (see Pitfall 5 — `valorTotal` may be `null`, or the Honorario record may not have been created/confirmed at all if formalizar's auto-creation is skipped/failed).

**Prevention:**
- Gate the print page's render on ALL THREE hooks' `isLoading`/`isError`, not just the primary one (Cliente in the existing precedent is the "primary"; here there may be a 3-way tie).
- Explicitly handle "Honorario not found for this processo" as a distinct state (not just a null-guarded blank field) — printing a "Termo de Honorários" for a processo with no honorario at all is a meaningless document and should probably be blocked with a clear message, not silently rendered with every value showing the `BLANK` placeholder.
- Reuse the exact `fmt()`/`BLANK` null-guard helper from `ficha/page.tsx` for every field pulled from all three entities, especially `valorTotal` (currency formatting AND null-guard both needed — `fmt()` today does a raw `String(value)` cast, which is fine for text fields but would need a currency-aware variant for `valorTotal` to avoid printing something like `1500` unformatted on a legal document).
- Cliente fields used in the Termo (nome, NIF, morada) went through the v2.7/v2.8 flattening — pull from the current flat columns (`cliente.nome`, `cliente.nif`, `cliente.morada`), not any legacy `dados_tipo` shape (already removed, but worth an explicit reminder given how recently that migration happened).

**Owner phase:** Frontend-integration phase, same phase that builds the Documentos tab / print flow — should explicitly list "loading/error gating across 3 hooks" and "currency-safe null-guard for valorTotal" as acceptance criteria, not assume the existing `fmt()` helper covers it as-is.

---

### Pitfall 7: Decisão's optional document attachment reuses the generic `Documento` entity, but `Documento` has no `decisao_id` column — the linkage needs a real FK or it becomes an untracked file with no ownership trail

**What goes wrong:** `Documento.java` currently has exactly two optional linkage columns: `processo_id` and `cliente_id` (line ~23-27). There is no generic "attach to any entity" pattern — every consumer of Documento so far has been either processo-scoped or cliente-scoped. If Decisão's optional attachment is implemented by just setting `documento.processoId` (reusing the existing processo linkage) with no `decisao_id` anywhere, the document becomes indistinguishable from any other processo-level document once inside the (new, v2.9) "Documentos" tab — there's no way to know "this specific PDF is the ruling attached to Decisão #4" versus "this is an unrelated document uploaded to the processo's general Documentos tab." This directly undermines the feature's own purpose (a Decisão with a traceable attached ruling).

**Prevention:**
- Add a nullable `decisao_id` column to `Documento` (following the exact precedent of `processo_id`/`cliente_id` — nullable UUID, no cascade complexity) rather than inventing a separate attachment mechanism, OR store the `documento_id` as a FK column on the `Decisao` entity itself pointing at an existing `Documento` row (simpler, one-directional, matches "optional attachment" framing better since Decisão is the owner of the relationship, not Documento).
- Whichever direction is chosen, ensure the tenant/ownership check added in Pitfall 3 (child.processoId == path {id}) is extended to also verify, when a `documento_id`/attachment is set, that the referenced Documento belongs to the same tenant AND the same processo — otherwise this reintroduces exactly the gap Phase 79's code review caught and fixed for `POST /documentos/upload` (`clienteId`/`processoId` ownership validation before persist, per the Key Decisions log) — don't regress that fix by adding a new unvalidated FK.

**Owner phase:** Backend-entity phase for Decisão — decide the FK direction explicitly as a design decision before implementation starts (this affects both the Decisão entity shape and the Documentos-tab query logic), and re-apply the Phase 79 ownership-validation pattern to whichever new endpoint sets this link.

## Minor Pitfalls

### Pitfall 8: New `origem` enum values risk a label/value mismatch between Portuguese display labels and stored enum constants

**What goes wrong:** The milestone spec gives the two origem values as "Petição Inicial" and "Notificações Avulsas" — human-readable Portuguese labels with accents and spaces. If these are stored verbatim as the enum/string value (rather than a stable code like `PETICAO_INICIAL`/`NOTIFICACOES_AVULSAS` with the accented label only in the UI layer), any future rename of the display label becomes a data migration, and accent/encoding issues become a real risk for exact-match filtering (`WHERE origem = 'Petição Inicial'`).

**Prevention:** Follow the existing `DocumentoTipo`/`conflictNivelEnum` precedent — store a stable uppercase/ASCII code server-side, map to the accented Portuguese label only in frontend display helpers (mirroring `conflictNivelToLabel()` in `web/src/lib/conflict-check.ts`).

**Owner phase:** Backend-entity phase (choose the stored enum values) — flag this as a 5-minute decision to make explicit rather than accidentally storing the label text via a copy-pasted string field.

---

### Pitfall 9: Facto's "ordering field" gets reindexed inconsistently on delete/insert, or two Factos silently share the same order value

**What goes wrong:** An explicit "ordem" field (as opposed to relying on `created_at` or a linked-list `nextId`) requires the backend to either auto-increment on insert (like `ProcessoFase`'s implicit chronological order) or accept a user-supplied position. If insert simply does `ordem = maxExistingOrdem + 1` without a per-processo scope, two Factos on different processos could collide in ways that don't matter, but on the *same* processo an insert-in-the-middle or delete operation that doesn't shift subsequent `ordem` values leaves gaps or (worse) ties, and any UI `sort by ordem` becomes non-deterministic for tied rows.

**Prevention:** Scope the max-order lookup to `processo_id` explicitly (`SELECT MAX(ordem) FROM t_facto WHERE processo_id = ?`, tenant-checked the same way `ProcessoFase` creation resolves its catalog), and decide upfront whether reordering (drag-and-drop, "move up/down") is in scope for this milestone — if not, at minimum ensure delete doesn't need to compact the sequence (gaps in `ordem` are harmless for sort-only use; only tie-breaking on insert matters).

**Owner phase:** Backend-entity phase for Facto — small but worth one explicit line in that phase's plan ("ordem assignment scoped per processo_id, ties broken by created_at as secondary sort").

## Phase-Specific Warnings

| Phase Topic | Likely Pitfall | Mitigation |
|-------------|----------------|------------|
| Backend: add `juizo`/`origem` columns to Processo | Pitfall 1 (mapping layer), Pitfall 2 (dual validation layers), Pitfall 8 (enum value stability) | Add fields to entity + both `CAMPOS_MINIMOS_POR_TIPO` and new intake-validation; choose stable enum codes upfront |
| Backend: Decisão/Facto/Testemunha entities + endpoints | Pitfall 3 (ownership double-check on PUT/DELETE), Pitfall 7 (Documento FK direction), Pitfall 9 (ordem scoping) | Copy the `ProcessoFase` PUT pattern (parent tenant check + child.processoId re-check), not the simpler Parte/Movimentacao pattern; decide Documento↔Decisão FK direction before coding |
| Backend: formalizar side-effect (auto Honorario) | Pitfall 4 (idempotency), Pitfall 5 (valor auto-default, RBAC scope mismatch) | Existence-check before create (not just state guard); `valorTotal` starts null; confirmation step requires `financeiro:edit` separately from `processos:manage` |
| Frontend: intake wizard step 1 (origem field) | Pitfall 1, Pitfall 2 | `processoFormSchema` origem field must be `z.enum(...)`, not `optionalTrimmedString`; wire `normalizeProcesso`/`toProcessoApiPayload` in the same PR |
| Frontend: Documentos tab (reuse Clientes v2.8 pattern) | Pitfall 7 (attachment ownership validation regression) | Re-apply Phase 79's tenant/ownership pre-persist check to any new processo-scoped or decisao-scoped upload path |
| Frontend: Termo de Honorários print page | Pitfall 6 (3-hook loading/error gating, currency null-guard) | Gate render on all 3 hooks; extend `fmt()` pattern with a currency-safe variant; block/flag print when Honorario or valorTotal is missing |
| Cross-cutting: RBAC on all new endpoints | Frontend `hasScopedPermission` vs backend `@PreAuthorize` mismatch (project's known recurring pitfall #3, not re-derived here per instructions) | Explicit side-by-side checklist per new endpoint: backend scope string vs frontend `permissions.can.*` call, reviewed together, not independently |

## Sources

- `backend/src/main/java/com/lexcv/controllers/ResourceController.java` (read directly: lines 60-1260, 1521-1660, 2171-2290) — formalizar/intake/conflict-check logic, Parte/Fase/Movimentacao/Honorario CRUD patterns, tenant-check precedents
- `backend/src/main/java/com/lexcv/models/Processo.java`, `Honorario.java`, `Documento.java`, `Cliente.java` — entity field shapes, `@JsonProperty`/`@Column` conventions, `honorariosPropostos` JSON-converted field
- `web/src/hooks/use-processos.ts` (lines 1-115) — `normalizeProcesso`/`toProcessoApiPayload` manual mapping layer (root cause of Pitfall 1)
- `web/src/schemas/processos.ts` — `processoFormSchema` current field requiredness
- `web/src/app/(dashboard)/processos/novo/page.tsx` — 3-step intake→conflict-check→formalizar wizard implementation
- `web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx` — existing `window.print()` + `fmt()`/`BLANK` null-guard pattern, direct precedent for Termo de Honorários
- `.planning/PROJECT.md` — Key Decisions log (Phase 79 ownership-validation fix, `@JsonProperty` cirúrgico decision, `dados_tipo` flattening history) and milestone-provided pitfall history
