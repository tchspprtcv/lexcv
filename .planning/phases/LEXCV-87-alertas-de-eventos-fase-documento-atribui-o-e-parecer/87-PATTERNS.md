# Phase 87: Alertas de Eventos — Fase, Documento, Atribuição e Parecer - Pattern Map

**Mapped:** 2026-07-08
**Files analyzed:** 8 (2 modified backend controllers + 1 modified service + 3 modified frontend files + 1 modified repository-consuming call site + 1 possible type addition)
**Analogs found:** 8 / 8

---

## Orienting fact (read first)

`NotificacaoService.java` (Phase 86) was deliberately built anticipating this exact phase. The
comment already in the file, directly above the package-private `notificarAdmins` helper, reads:

> `backend/src/main/java/com/lexcv/services/NotificacaoService.java:76-80`
> ```java
> // Fan-out: uma linha independente por cada ADMIN atual do tenant, cada uma com o seu próprio
> // estado "lida" — nunca uma linha partilhada com uma flag "é admin". Package-private porque
> // só é chamado a partir deste serviço (e do teste, no mesmo pacote); a Phase 87 acrescentará
> // os métodos públicos notificarFaseEntrada/notificarDocumentoNovo/etc. que reutilizam este
> // helper — não são adicionados agora (gatilhos reais são fora do âmbito desta fase).
> ```

This tells the planner exactly where the bulk of the new logic belongs: **new public convenience
methods on `NotificacaoService` itself** (e.g. `notificarFaseEntrada`, `notificarDocumentoNovo`,
`notificarProcessoAtribuido`, `notificarParecerAtribuido`), each composing the existing
`criar(...)` (primary recipient) + `notificarAdmins(...)` (fan-out) calls. The 4 controller call
sites then become thin — one method call each — rather than reimplementing fan-out logic inline
at every trigger.

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `backend/src/main/java/com/lexcv/services/NotificacaoService.java` (extend) | service | event-driven | same file — `criar()` / `notificarAdmins()` (lines 28-87) | exact — self-extension, explicitly anticipated |
| `backend/src/main/java/com/lexcv/controllers/ResourceController.java` — `createProcessoFase` (add trigger) | controller | event-driven | same method, in-place | exact |
| `backend/src/main/java/com/lexcv/controllers/ResourceController.java` — `uploadDocumento` (add trigger) | controller | file-I/O + event-driven | same method, in-place | exact |
| `backend/src/main/java/com/lexcv/controllers/ResourceController.java` — `createProcesso` (add trigger, initial assignment) | controller | CRUD + event-driven | same method, in-place | exact |
| `backend/src/main/java/com/lexcv/controllers/ResourceController.java` — **new** reassignment endpoint (name TBD) | controller | request-response | `ParecerController.atribuirAdvogado` (lines 236-289) | role-match, near-exact structural twin |
| `backend/src/main/java/com/lexcv/controllers/ParecerController.java` — `createSolicitacao` (add trigger) | controller | CRUD + event-driven | same method, in-place | exact |
| `backend/src/main/java/com/lexcv/controllers/ParecerController.java` — `atribuirAdvogado` (add trigger) | controller | request-response + event-driven | same method, in-place | exact |
| `web/src/app/(dashboard)/processos/[id]/page.tsx` — new inline `Reatribuir` Dialog+AlertDialog component | component | request-response | `pareceres/[id]/page.tsx` `EntregarParecerDialog` (428-525) **+** same-file "Adicionar Parte" Dialog (1520-1589) | exact — both precedents named in CONTEXT.md/UI-SPEC.md |
| `web/src/hooks/use-processos.ts` — new `useReatribuirResponsavel(processoId)` | hook (mutation) | request-response | `useExecutarTransicao` (664-688) for invalidation breadth + `useUpdateProcesso` (220-238) for payload/response shape | exact |
| `web/src/types/processos.ts` — optional new request type | type | n/a | `ProcessoUpdateRequest` / `WorkflowResponse` (249-255) | role-match, likely not needed (see below) |

**Convention warning (important):** every dialog CONTEXT.md/UI-SPEC.md cite as precedent
(`EntregarParecerDialog`, "Adicionar Parte/Fase/Decisão/Facto/Testemunha") is an **inline sibling
function defined in the same route file**, not a separate component under `components/shared/`.
Do not create a new file like `components/shared/reatribuir-dialog.tsx` — add a
`ReatribuirResponsavelControl` (or similarly named) function directly inside
`web/src/app/(dashboard)/processos/[id]/page.tsx`, next to `EntregarParecerDialog`'s counterpart
pattern.

---

## Pattern Assignments

### Backend — `NotificacaoService.java` (service, event-driven)

**File:** `backend/src/main/java/com/lexcv/services/NotificacaoService.java`

**Existing write-path signatures to compose from** (lines 28-87):
```java
public Notificacao criar(UUID tenantId, UUID destinatarioId, String categoria, String titulo,
                          String mensagem, String entidadeTipo, String entidadeId, String linkUrl) {
    if (tenantId == null || destinatarioId == null) {
        throw new IllegalArgumentException("tenantId e destinatarioId são obrigatórios");
    }
    userRepository.findById(destinatarioId)
            .filter(u -> tenantId.equals(u.getTenantId()))
            .orElseThrow(() -> new IllegalArgumentException(
                    "destinatarioId não pertence ao tenant informado"));
    ...
}

@Transactional
void notificarAdmins(UUID tenantId, String categoria, String titulo, String mensagem,
                      String entidadeTipo, String entidadeId, String linkUrl) {
    for (User admin : userRepository.findByTenantIdAndRoleName(tenantId, "ADMIN")) {
        criar(tenantId, admin.getId(), categoria, titulo, mensagem, entidadeTipo, entidadeId, linkUrl);
    }
}
```

**Critical guard — `criar()` throws on null `destinatarioId`.** `Processo.responsavelId` and
`Prazo.responsavelId` are both nullable (confirmed in `Processo.java` and by `createProcesso`'s own
`if (processo.getResponsavelId() != null)` guard, `ResourceController.java:970`). Any new wrapper
method that reads `processo.getResponsavelId()` to notify (FASE_ENTRADA, DOCUMENTO_NOVO's
processo-linked branch) **must** null-check before calling `criar()`, or it will throw
`IllegalArgumentException` for any processo that has no responsável assigned yet — a very common
state pre-Phase-87 (today only `createProcesso` can set it, and it's optional there).

**Actor exclusion needs a small signature extension.** CONTEXT.md locks actor-exclusion for exactly
2 of the 4 categories: DOCUMENTO_NOVO ("O ator que fez o upload é sempre excluído") and
PARECER_ATRIBUIDO ("O ator ... é sempre excluído"). FASE_ENTRADA and PROCESSO_ATRIBUIDO have no such
requirement stated. Since `notificarAdmins` fans out to *every* current ADMIN with no actor
parameter today, the cleanest change is to add an overload:
```java
@Transactional
void notificarAdmins(UUID tenantId, String categoria, String titulo, String mensagem,
                      String entidadeTipo, String entidadeId, String linkUrl, UUID excluirUserId) {
    for (User admin : userRepository.findByTenantIdAndRoleName(tenantId, "ADMIN")) {
        if (excluirUserId != null && excluirUserId.equals(admin.getId())) continue;
        criar(tenantId, admin.getId(), categoria, titulo, mensagem, entidadeTipo, entidadeId, linkUrl);
    }
}
```
keeping the existing 7-arg `notificarAdmins` as-is (so `NotificacaoServiceTest`'s existing call at
line 146 keeps compiling) and having it delegate to the 8-arg version with `excluirUserId = null`.

**Categoria/entidadeTipo conventions already fixed by Phase 86** (do not invent new ones):
- `Notificacao.java:26` javadoc comment enumerates the exact 4 categoria values this phase needs:
  `FASE_ENTRADA | DOCUMENTO_NOVO | PROCESSO_ATRIBUIDO | PARECER_ATRIBUIDO`.
- `NotificacaoServiceTest.java` already exercises `"FASE_ENTRADA"` (line 55) and `"DOCUMENTO_NOVO"`
  (line 146) as literal strings, plus `entidadeTipo` values `"processo"` (lowercase, singular) and
  `"documento"`. For parecer, mirror `ParecerController`'s own `AuditLog` `entidadeTipo` convention:
  `"parecer_solicitacao"` (used at `ParecerController.java:160,283,377,489`).

**Recommended shape for the 4 new public methods** (sketch, not prescriptive — planner decides
exact parameter lists):
```java
public void notificarFaseEntrada(UUID tenantId, UUID processoId, UUID responsavelId,
                                  String numeroProcesso, String nomeFase, String linkUrl) {
    if (responsavelId != null) {
        criar(tenantId, responsavelId, "FASE_ENTRADA",
              "Nova fase", "O processo " + numeroProcesso + " entrou na fase " + nomeFase,
              "processo", processoId.toString(), linkUrl);
    }
    notificarAdmins(tenantId, "FASE_ENTRADA", "Nova fase",
                     "O processo " + numeroProcesso + " entrou na fase " + nomeFase,
                     "processo", processoId.toString(), linkUrl);
}
```
Repeat this shape (guard + primary `criar()` + `notificarAdmins()`, with `excluirUserId` threaded
through for DOCUMENTO_NOVO/PARECER_ATRIBUIDO) for the other 3 categories.

---

### Backend — `ResourceController.createProcessoFase` (controller, event-driven — Trigger 1/4: FASE_ENTRADA)

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:1594-1617`

**Current method (no audit, no notification today):**
```java
@PreAuthorize("hasAuthority('processos:edit')")
@PostMapping("/processos/{id}/fases")
public ResponseEntity<?> createProcessoFase(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
    Processo processo = processoRepository.findById(id).orElse(null);
    if (processo == null || !processo.getTenantId().equals(getTenantId())) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo não encontrado"));
    }
    String faseNome = (String) body.get("nome");
    if (faseNome == null) {
        return ResponseEntity.badRequest().body(Map.of("message", "Nome da fase é obrigatório"));
    }

    FaseProcessual catalog = faseProcessualRepository.findByNome(faseNome)
            .orElseGet(() -> faseProcessualRepository.save(FaseProcessual.builder().nome(faseNome).build()));

    ProcessoFase pf = ProcessoFase.builder()
            .processoId(id)
            .faseId(catalog.getId())
            .dataInicio(LocalDate.now())
            .ativa(true)
            .build();

    return ResponseEntity.status(HttpStatus.CREATED).body(processoFaseRepository.save(pf));
}
```
**Splice point:** after `processoFaseRepository.save(pf)`, before the `return`. No actor extraction
needed (CONTEXT.md does not require actor exclusion here). Use `processo.getResponsavelId()` +
`processo.getNumeroProcesso()` + `faseNome` for the message. `linkUrl` per NOTF-15 should point at
the Fases tab of the ficha — CONTEXT.md's own decision says construct it however the ficha
currently supports; UI-SPEC.md found the ficha's tab state is **plain local React state, not
URL-driven today** (`processos/[id]/page.tsx:205`), so a literal `?tab=fases` query string is
correct backend copy but won't auto-navigate until/unless the frontend is also updated — that
frontend wiring is explicitly **not required by this phase** (Phase 87 builds no notification
consumption UI; see CONTEXT.md Integration Points).

---

### Backend — `ResourceController.uploadDocumento` (controller, file-I/O + event-driven — Trigger 2/4: DOCUMENTO_NOVO)

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:2395-2483`

**Relevant excerpt (tenant validation + entity construction, both branches):**
```java
if (clienteId != null) {
    Cliente cliente = clienteRepository.findById(clienteId).orElse(null);
    if (cliente == null || !cliente.getTenantId().equals(getTenantId())) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "clienteId não pertence a este tenant"));
    }
}
if (processoId != null) {
    Processo processo = processoRepository.findById(processoId).orElse(null);
    if (processo == null || !processo.getTenantId().equals(getTenantId())) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "processoId não pertence a este tenant"));
    }
}
...
Documento saved = documentoRepository.save(documento);
return ResponseEntity.status(HttpStatus.CREATED).body(saved);
```
**Splice point:** after `Documento saved = documentoRepository.save(documento);`, before the
`return`. This method currently has **no** `Authentication`/`UserPrincipal` extraction at all — it
must be added to get the actor for the required exclusion:
```java
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
UUID atorId = principal.getUserId();
```
(exact pattern already used elsewhere in this file, e.g. line 1377 `principal.getUserId()`, and in
`ParecerController.java:154-155`).

**Branching logic per CONTEXT.md (processo has precedence over cliente):**
```java
if (processoId != null) {
    // notify processo.responsavelId (null-guarded) + ADMIN, excluding atorId
} else if (clienteId != null) {
    // notify ClienteAdvogado + ClienteAdministrativo team members + ADMIN, excluding atorId
}
```
`ClienteAdvogadoRepository`/`ClienteAdministrativoRepository` are **already injected** into
`ResourceController` (fields at lines 69-70) — no new repository wiring needed:
```java
List<ClienteAdvogado> advogados = clienteAdvogadoRepository.findByClienteIdAndTenantId(clienteId, tenantId);
List<ClienteAdministrativo> administrativos = clienteAdministrativoRepository.findByClienteIdAndTenantId(clienteId, tenantId);
// notify each advogados.get(i).getUserId() / administrativos.get(i).getUserId(), skipping atorId
```

---

### Backend — `ResourceController.createProcesso` (controller, CRUD + event-driven — Trigger 3a/4: PROCESSO_ATRIBUIDO, initial assignment)

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:965-979`

```java
@PreAuthorize("hasAuthority('processos:manage')")
@PostMapping("/processos")
public ResponseEntity<?> createProcesso(@RequestBody Processo processo) {
    UUID tenantId = getTenantId();
    processo.setTenantId(tenantId);
    if (processo.getResponsavelId() != null) {
        User responsavel = userRepository.findById(processo.getResponsavelId()).orElse(null);
        if (responsavel == null || !tenantId.equals(responsavel.getTenantId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "responsavelId não pertence a este tenant"));
        }
    }
    Processo saved = processoRepository.save(processo);
    return ResponseEntity.status(HttpStatus.CREATED).body(saved);
}
```
**This is also the exact tenant-validation block to copy verbatim** into the new reassignment
endpoint (see below) — CONTEXT.md explicitly says "Validar que o novo `responsavelId` pertence ao
tenant, exatamente como já acontece em `createProcesso`."

**Splice point:** after `Processo saved = processoRepository.save(processo);`, only inside the
`if (processo.getResponsavelId() != null)` branch already present — call
`notificarProcessoAtribuido(tenantId, saved.getId(), saved.getResponsavelId(), saved.getNumeroProcesso(), <linkUrl>)`.

---

### Backend — new reassignment endpoint (controller, request-response — Trigger 3b/4: PROCESSO_ATRIBUIDO, reassignment)

**Primary named analog:** `ParecerController.atribuirAdvogado`, `backend/src/main/java/com/lexcv/controllers/ParecerController.java:235-289`
```java
@PreAuthorize("hasAuthority('pareceres:edit')")
@PutMapping("/{id}/atribuir")
@Transactional
public ResponseEntity<?> atribuirAdvogado(@PathVariable UUID id, @RequestBody Map<String, String> body) {
    UUID tenantId = getTenantId();

    String advogadoIdRaw = body.get("advogadoId");
    if (advogadoIdRaw == null || advogadoIdRaw.isBlank()) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "advogadoId é obrigatório"));
    }
    UUID advogadoId;
    try {
        advogadoId = UUID.fromString(advogadoIdRaw);
    } catch (IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "advogadoId inválido"));
    }

    ParecerSolicitacao solicitacao = parecerSolicitacaoRepository.findById(id).orElse(null);
    if (solicitacao == null || !solicitacao.getTenantId().equals(tenantId)) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Solicitação não encontrada"));
    }
    ...
    solicitacao.setAdvogadoId(advogadoId);
    solicitacao.setStatus("EM_ELABORACAO");
    ParecerSolicitacao saved = parecerSolicitacaoRepository.save(solicitacao);
    // Audit record ...
    return ResponseEntity.ok(saved);
}
```

**What to change for the Processo case:**
- `@PreAuthorize("hasAuthority('processos:manage')")` — CONTEXT.md locks this to `manage`, not
  `edit` (distinct from every other `/processos/{id}/...` write, which uses `processos:edit`).
- Route: planner's discretion per CONTEXT.md — e.g. `@PutMapping("/processos/{id}/atribuir")`
  mirrors `atribuirAdvogado`'s exact route shape (`/{id}/atribuir`) one level up.
- Request body: either mirror `atribuirAdvogado`'s `Map<String, String> body` +
  `UUID.fromString(...)` manual-parse shape, **or** reuse `createProcesso`'s tenant-validation block
  verbatim against `@RequestBody Processo payload` reading `payload.getResponsavelId()` (avoids a
  parse step, and is textually identical to the already-cited precedent). Both are legitimate,
  already-attested shapes in this codebase; `TransicaoRequest` (`ResourceController.java:1311`,
  a `record` DTO consumed via `@RequestBody(required = false)`) is a third, more typed option if a
  small dedicated request record is preferred.
- Response: `ResponseEntity.ok(processoRepository.save(processo))` — same as `atribuirAdvogado`'s
  `ResponseEntity.ok(saved)` and identical to what `updateProcesso` already returns (raw entity,
  camelCase fields — the frontend's `normalizeProcesso()` already handles this shape).
- Splice notification call after the save, calling the new
  `notificarProcessoAtribuido(...)` wrapper — no actor exclusion required per CONTEXT.md (not
  stated, unlike the other two categories).

---

### Backend — `ParecerController.createSolicitacao` / `atribuirAdvogado` (controller — Trigger 4/4: PARECER_ATRIBUIDO)

**File:** `backend/src/main/java/com/lexcv/controllers/ParecerController.java`

**`createSolicitacao` advogado-at-creation branch** (lines 141-149):
```java
if (body.getAdvogadoId() != null) {
    User advogado = validateAdvogado(body.getAdvogadoId(), tenantId);
    if (advogado == null) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "advogadoId não pertence a este tenant ou não tem papel ADVOGADO"));
    }
    solicitacao.setAdvogadoId(body.getAdvogadoId());
    solicitacao.setStatus("EM_ELABORACAO");
}
ParecerSolicitacao saved = parecerSolicitacaoRepository.save(solicitacao);
```
Splice point: after `saved = parecerSolicitacaoRepository.save(solicitacao)`, only inside the
`if (body.getAdvogadoId() != null)` branch. Actor = `principal.getUserId()` (already extracted at
lines 154-155 for the `AuditLog` write, immediately reusable) — exclude actor from the notify call
per CONTEXT.md (though self-assignment at creation time is an edge case).

**`atribuirAdvogado`** (lines 236-289, full method quoted above) — splice after
`ParecerSolicitacao saved = parecerSolicitacaoRepository.save(solicitacao);`, using the same
`principal` already extracted at lines 277-278 for the `AuditLog` write. CONTEXT.md: notify **only**
the new advogado, never the previous one — `atribuirAdvogado` doesn't currently read the *previous*
`advogadoId` before overwriting it, so no extra lookup is needed (just don't add one).

**Both call sites need `NotificacaoService` added as a constructor-injected field** —
`ParecerController` does not have it today (its field list, lines 40-46, has no `NotificacaoService`).

---

### Frontend — Reatribuir Dialog+AlertDialog (component, request-response)

**Primary analog — two-step Dialog→AlertDialog flow:** `web/src/app/(dashboard)/pareceres/[id]/page.tsx:428-525` (`EntregarParecerDialog`, quoted in full below). **Do not copy its color classes** —
`bg-destructive text-destructive-foreground hover:bg-destructive/90` (lines 471-473, 517) resolves
to nothing because no `--destructive` Tailwind token exists in this codebase (confirmed:
`web/src/app/globals.css` only defines `--background`/`--foreground`) — this is a known,
UI-SPEC-flagged pre-existing bug. Copy the **structural** pattern only (state shape, error/success
handling, footer composition); use `variant="outline"` / accent-blue per UI-SPEC's Copywriting
Contract instead.

```typescript
function EntregarParecerDialog({ solicitacaoId, versoes }: {...}) {
  const [confirmOpen, setConfirmOpen] = React.useState(false);
  const [selectedVersaoIdState, setSelectedVersaoId] = React.useState<string | null>(null);
  const [entregaError, setEntregaError] = React.useState<string | null>(null);
  const entregar = useEntregarParecer(solicitacaoId);

  const handleEntregar = async () => {
    if (!selectedVersaoId) return;
    setEntregaError(null);
    try {
      await entregar.mutateAsync({ versaoFinalId: selectedVersaoId });
      toast.success("Parecer entregue com sucesso.");
      setConfirmOpen(false);
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Não foi possível entregar o parecer. Verifique a ligação e tente novamente.";
      setEntregaError(msg);
      toast.error(msg);
    }
  };

  return (
    <AlertDialog open={confirmOpen} onOpenChange={(next) => { if (entregar.isPending) return; setConfirmOpen(next); }}>
      <AlertDialogTrigger asChild>
        <Button type="button" /* ... */>Entregar Parecer</Button>
      </AlertDialogTrigger>
      <AlertDialogContent onEscapeKeyDown={(e) => { if (entregar.isPending) e.preventDefault(); }}>
        <AlertDialogHeader>
          <AlertDialogTitle>Entregar Parecer</AlertDialogTitle>
          <AlertDialogDescription>...</AlertDialogDescription>
        </AlertDialogHeader>
        {/* field */}
        {entregaError ? <p className="text-sm text-red-600 px-1">{entregaError}</p> : null}
        <AlertDialogFooter>
          <AlertDialogCancel disabled={entregar.isPending}>Cancelar</AlertDialogCancel>
          <AlertDialogAction disabled={entregar.isPending || !selectedVersaoId}
            onClick={(e) => { e.preventDefault(); void handleEntregar(); }}>
            {entregar.isPending ? "A entregar..." : "Confirmar Entrega"}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
```
Note this component has **no Dialog step 1** — it goes straight to AlertDialog because the version
selector lives inside the AlertDialogContent itself. Phase 87's control needs a genuine **two-modal**
flow (Dialog for picking the user, then AlertDialog for confirming) per UI-SPEC's Interaction Flow —
for the Dialog-step shell, use the second analog below instead, and only borrow
`EntregarParecerDialog`'s **error/success/pending-state handling shape** (the
`useState` + `try/catch` + dual toast/inline-error pattern) for the AlertDialog step.

**Second analog — Dialog shell + user `<select>` + footer:** `web/src/app/(dashboard)/processos/[id]/page.tsx:1520-1589` ("Adicionar Parte", full Dialog) and the Prazo-responsável `<select>` at
lines 1155-1169:
```typescript
<Dialog open={addParteModal} onOpenChange={setAddParteModal}>
  <DialogTrigger asChild>
    <Button type="button" variant="outline" size="sm" className="rounded-none" onClick={onOpenAddParte}>
      Adicionar Parte
    </Button>
  </DialogTrigger>
  <DialogContent>
    <DialogHeader><DialogTitle>Adicionar Parte</DialogTitle></DialogHeader>
    <form className="space-y-4" onSubmit={...}>
      {/* fields */}
      <DialogFooter>
        <Button type="button" variant="outline" className="rounded-none" onClick={() => setAddParteModal(false)}>
          Cancelar
        </Button>
        <Button type="submit" className="rounded-none" disabled={...}>
          {... ? "A guardar..." : "Adicionar"}
        </Button>
      </DialogFooter>
    </form>
  </DialogContent>
</Dialog>
```
```typescript
// processos/[id]/page.tsx:1155-1169 — exact <select> className to copy verbatim (per UI-SPEC)
<select
  id="prazo_responsavel"
  className="h-10 w-full bg-white dark:bg-[#020617] rounded-none border border-slate-300 dark:border-slate-700 px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
  {...prazoForm.register("responsavelId")}
>
  <option value="">— Sem responsável —</option>
  {(adminUsers.data ?? []).map((u) => (
    <option key={u.id} value={u.id}>{u.nome}</option>
  ))}
</select>
```
```typescript
// processos/[id]/page.tsx:429-433 — reset-on-open convention (mirror for the new dialog's onOpen)
const onOpenAddParte = () => {
  parteForm.reset({ tipo: undefined, nome: "", nif: undefined });
  setParteServerError(null);
  setAddParteModal(true);
};
```

**Exact insertion point (Workflow card, Responsável `dd`):** `processos/[id]/page.tsx:894-902`
```typescript
<dt className="text-neutral-500 dark:text-neutral-400">Responsável</dt>
<dd className="col-span-2 font-medium flex items-center gap-1">
  <User className="h-[14px] w-[14px] text-slate-400" />
  {workflow.data.responsavelNome ? (
    workflow.data.responsavelNome
  ) : (
    <span className="text-slate-400 dark:text-slate-500 italic">Não atribuído</span>
  )}
</dd>
```
Insert the new `Reatribuir` trigger button (gated by `canManageProcessos`, already computed and
passed into `ProcessoDetailContent` at lines 182-183/197) immediately after this `{...}` block,
inside the same `dd`. `processo.data?.numero` (line 671: `{processo.data?.numero ?? processo.data?.titulo ?? "…"}`) is the field to use for the confirmation sentence's `{numero}`.

**Component imports needed** (both already used elsewhere in this same file, so no new package
installs): `Dialog`/`DialogContent`/`DialogHeader`/`DialogTitle`/`DialogFooter`/`DialogTrigger` from
`@/components/ui/dialog`; `AlertDialog`/`AlertDialogAction`/`AlertDialogCancel`/`AlertDialogContent`/
`AlertDialogDescription`/`AlertDialogFooter`/`AlertDialogHeader`/`AlertDialogTitle`/
`AlertDialogTrigger` from `@/components/ui/alert-dialog` (this file does not currently import
`AlertDialog*` — only `pareceres/[id]/page.tsx` does — so this import block is new to this file).

---

### Frontend — `useReatribuirResponsavel(processoId)` (hook, request-response)

**File:** `web/src/hooks/use-processos.ts`

**Analog 1 — invalidation breadth**, `useExecutarTransicao` (lines 664-688):
```typescript
export function useExecutarTransicao(processoId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (args: { acao: string; payload?: TransicaoRequest }) => {
      const response = await apiFetch<ProcessoApi>(
        `/processos/${encodeURIComponent(processoId)}/transicao/${args.acao}`,
        { method: "POST", body: JSON.stringify(args.payload ?? {}) },
      );
      return normalizeProcesso(response);
    },
    onSuccess: async (updated) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["processos", "list"] }),
        queryClient.setQueryData(["processos", "detail", processoId], updated),
        queryClient.invalidateQueries({ queryKey: ["processos", "workflow", processoId] }),
        queryClient.invalidateQueries({ queryKey: ["processos", "movimentacoes", processoId] }),
        queryClient.invalidateQueries({ queryKey: ["processos", "timeline", processoId] }),
      ]);
    },
  });
}
```
This is the **exact** invalidation set UI-SPEC.md calls for (`list` + `detail` + `workflow`) — copy
this `onSuccess` shape verbatim, dropping `movimentacoes`/`timeline` (reassignment doesn't affect
those) unless the planner decides a timeline entry should also be recorded.

**Analog 2 — simple POST-mutation shape**, `useFormalizarProcesso` (lines 630-648):
```typescript
export function useFormalizarProcesso(processoId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async () => {
      const response = await apiFetch<ProcessoApi>(
        `/processos/${encodeURIComponent(processoId)}/formalizar`,
        { method: "POST" },
      );
      return normalizeProcesso(response);
    },
    onSuccess: async (updated) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["processos", "list"] }),
        queryClient.setQueryData(["processos", "detail", processoId], updated),
      ]);
    },
  });
}
```
Naming convention: both are verb-based (`useFormalizarProcesso`, `useExecutarTransicao`) — UI-SPEC's
own recommendation, `useReatribuirResponsavel(processoId)`, matches this exactly.

**Payload/body shape**, `useUpdateProcesso` (lines 220-238) — for the `mutationFn` body-shape if the
backend endpoint takes a JSON body (`{ responsavelId }`) rather than a query param:
```typescript
export function useUpdateProcesso(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (payload: ProcessoUpdateRequest) => {
      const response = await apiFetch<ProcessoApi>(`/processos/${encodeURIComponent(id)}`, {
        method: "PUT",
        body: JSON.stringify(toProcessoApiPayload(payload satisfies ProcessoUpdateRequest)),
      });
      return normalizeProcesso(response);
    },
    onSuccess: async (updated) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["processos", "list"] }),
        queryClient.setQueryData(["processos", "detail", id], updated),
      ]);
    },
  });
}
```

**`useAdminUsers()`** — already the exact hook needed for the picker's data source, no change
required: `web/src/hooks/use-admin.ts:7-16`
```typescript
export function useAdminUsers(options?: { enabled?: boolean }) {
  const enabled = typeof window !== "undefined" && (options?.enabled ?? true);
  return useQuery({
    queryKey: ["admin", "users"],
    queryFn: () => apiFetch<MockUser[]>("/admin/users"),
    enabled,
    staleTime: 30_000,
  });
}
```

**Toast helpers**, `web/src/hooks/use-toast.ts:219-224` (already imported in `processos/[id]/page.tsx`):
```typescript
toast.success = (message, options) => toast({ title: "Sucesso", description: message, variant: "default", ...options });
toast.error = (message, options) => toast({ title: "Erro", description: message, variant: "destructive", ...options });
```

**Permission gate**, `web/src/lib/permissions.ts:24-36` (`ACTION_FALLBACKS.manage = ["manage"]` —
strictly does not fall back from `edit`, confirming `canManageProcessos` and `canEditProcessos` are
genuinely distinct gates, matching CONTEXT.md's explicit lock to `processos:manage`):
```typescript
export function hasScopedPermission(permissions, scope, action) {
  if (!permissions?.length) return false;
  const allowed = resolveScopedPermissions(scope, action);
  return allowed.some((permission) => permissions.includes(permission));
}
```
Already surfaced in the page as `permissions.can.manage("processos")` → `canManageProcessos`
(`processos/[id]/page.tsx:183`).

---

## Shared Patterns

### 1. `NotificacaoService.criar(...)` is the only write path
**Source:** `backend/src/main/java/com/lexcv/services/NotificacaoService.java:28-61`
**Apply to:** all 6 backend call sites (createProcessoFase, uploadDocumento ×2 branches,
createProcesso, new reassignment endpoint, ParecerController createSolicitacao + atribuirAdvogado).
No call site should call `notificacaoRepository.save(...)` directly.

### 2. `responsavelId` tenant-ownership validation
**Source:** `ResourceController.createProcesso`, lines 970-976 (quoted above).
**Apply to:** the new reassignment endpoint, verbatim, per CONTEXT.md's explicit instruction.

### 3. Null-guard before notifying a `responsavelId`
**Apply to:** `createProcessoFase`, `uploadDocumento`'s processo-linked branch. `Processo.responsavelId`
is nullable; `NotificacaoService.criar()` throws `IllegalArgumentException` on a null
`destinatarioId`. Always wrap the primary-recipient `criar()` call in
`if (responsavelId != null) { ... }`; the ADMIN fan-out has no such restriction and should always fire.

### 4. Actor exclusion (2 of 4 categories only)
**Applies to:** DOCUMENTO_NOVO (upload endpoint) and PARECER_ATRIBUIDO (both ParecerController call
sites). **Does not apply** (per CONTEXT.md's literal wording) to FASE_ENTRADA or PROCESSO_ATRIBUIDO.
Actor is always `principal.getUserId()` from
`(UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal()` — already
extracted in `ParecerController` for both relevant methods (lines 154-155, 277-278); needs to be
newly extracted in `uploadDocumento`.

### 5. Categoria / entidadeTipo string constants
**Source:** `Notificacao.java:26` (categoria enum comment) + `NotificacaoServiceTest.java` (literal
usage) + `ParecerController.java` `AuditLog` calls (entidadeTipo precedent for parecer).
| Category | `categoria` | `entidadeTipo` |
|---|---|---|
| Fase | `FASE_ENTRADA` | `processo` |
| Documento | `DOCUMENTO_NOVO` | `documento` |
| Atribuição de processo | `PROCESSO_ATRIBUIDO` | `processo` |
| Atribuição de parecer | `PARECER_ATRIBUIDO` | `parecer_solicitacao` |

### 6. Cache invalidation breadth on the frontend mutation
**Source:** `useExecutarTransicao`, `web/src/hooks/use-processos.ts:664-688`.
**Apply to:** `useReatribuirResponsavel` — must invalidate/update at minimum
`["processos", "workflow", id]`, `["processos", "detail", id]`, `["processos", "list"]`. Missing the
`workflow` key is UI-SPEC's own flagged "most likely integration bug" (success toast fires but the
displayed name silently doesn't refresh).

### 7. Two-step Dialog→AlertDialog confirmation for sensitive-but-reversible actions
**Source:** `EntregarParecerDialog` (state-machine shape only, not its color classes) +
"Adicionar Parte" (Dialog shell). **Apply to:** the new Reatribuir control exclusively (no other
new UI in this phase).

---

## No Analog Found / Discretion Notes

| Item | Note |
|---|---|
| New Zod schema for the reassignment form | Likely **not needed**. `EntregarParecerDialog` — the phase's own named two-step precedent — manages its selection state with plain `React.useState`, not `react-hook-form`/Zod, because the "form" is a single `<select>` feeding a confirmation step, not a validated multi-field form. Recommend mirroring that (no schema file addition) unless the planner has a reason to diverge. |
| `?tab=fases` query-param tab state on `processos/[id]/page.tsx` | UI-SPEC.md confirms the ficha's tab state is plain local `React.useState<TabKey>` today, not URL-synced. Constructing a *working* deep link would require adding `useSearchParams()`-driven initialization — but Phase 87 "não constrói nenhuma UI de consumo de notificações" (CONTEXT.md); that consumption (and thus the payoff of a working link) is Phase 89's job. Treat the backend `linkUrl` as inert copy for now; do not feel compelled to wire the frontend tab state in this phase. |
| `notificarAdmins` actor-exclusion overload | Not a pre-existing file/pattern — this is a **new** signature the planner must add to `NotificacaoService.java` itself (see Shared Pattern/Core Pattern above). Flagged here because it's a modification to Phase-86-owned code, not a pure addition. |
| New reassignment endpoint's exact route + request shape | Explicitly left to planner/executor discretion by CONTEXT.md. Three concrete, already-attested shapes are documented above (`atribuirAdvogado`'s `Map<String,String>` manual-parse; `createProcesso`'s typed-entity `Processo payload`; `TransicaoRequest`-style dedicated `record` DTO). Pick one — do not invent a fourth shape. |

---

## Metadata

**Analog search scope:** `backend/src/main/java/com/lexcv/{controllers,services,models,repositories}`, `web/src/{app/(dashboard)/processos,app/(dashboard)/pareceres,hooks,lib,components/ui,types}`
**Files scanned:** 17 read directly (2 fully, 15 via targeted offset/limit or grep-then-read for files > 900 lines); plus phase 86 artifacts (`NotificacaoServiceTest.java`, `deferred-items.md`) and milestone `v2.6-REQUIREMENTS.md` for categoria/NOTF-ID cross-checks.
**Pattern extraction date:** 2026-07-08
