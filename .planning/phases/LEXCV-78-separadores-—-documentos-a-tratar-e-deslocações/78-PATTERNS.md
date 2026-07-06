# Phase 78: Separadores — Documentos a Tratar e Deslocações - Pattern Map

**Mapped:** 2026-07-06
**Files analyzed:** 1 (single file, modified in place — pure JSX relocation, no new files)
**Analogs found:** 1 / 1 (self-referential — the analog for the new tab branches is the sibling tab-branch pattern already in the same file)

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|--------------------|------|-----------|-----------------|----------------|
| `web/src/app/(dashboard)/clientes/[id]/page.tsx` | component (page, client-side form + tabbed detail view) | CRUD (local-state staging, persisted on parent form submit) | itself — sibling tab branches `tab === "processos"` / `tab === "pareceres"` (lines 1066-1077) and their target functions `ClienteProcessosTab` (1123-1192) / `ClienteParecerTab` (1213+) | exact (same file, same component, same conditional-render idiom) |

Only one file is touched. There is no external analog to search for — the "pattern to copy" is the existing sibling tab-branch structure in the very same file, plus the existing dialog-reset `useEffect` that must be widened.

---

## Pattern Assignments

### `web/src/app/(dashboard)/clientes/[id]/page.tsx` (component, CRUD/local-state-staging)

**Analog:** itself (sibling tab branches + existing `PlaceholderEmBreve` slots + existing dialog-reset `useEffect`)

#### A. State declarations (already exist, do NOT move — stay at `ClienteDetailPage`/`ClienteDetailContent` component scope)

Lines 186-202:
```tsx
  // Intake list state — managed outside react-hook-form, synced into the PUT payload on submit.
  const [documentosEntregues, setDocumentosEntregues] = React.useState<DocumentoEntregue[]>([]);
  const [documentosATratar, setDocumentosATratar] = React.useState<DocumentoATratar[]>([]);
  const [deslocacoes, setDeslocacoes] = React.useState<Deslocacao[]>([]);

  const [addDocEntreModal, setAddDocEntreModal] = React.useState(false);
  const [newDocEntre, setNewDocEntre] = React.useState<{ descricao: string; data: string }>({ descricao: "", data: "" });

  const [addDocATratarModal, setAddDocATratarModal] = React.useState(false);
  const [newDocATratar, setNewDocATratar] = React.useState<{ descricao: string }>({ descricao: "" });

  const [addDeslocacaoModal, setAddDeslocacaoModal] = React.useState(false);
  const [newDeslocacao, setNewDeslocacao] = React.useState<{ descricao: string; local: string; data: string }>({
    descricao: "",
    local: "",
    data: "",
  });
```

**Do not touch this block.** Both lists are read/written by `onSubmit` (313-314: `documentosATratar: documentosATratar, deslocacoes: deslocacoes`), `onCancel` (337-338), and the load-effect (263-265), all of which live outside the tab-conditional JSX. Only the *rendering* of the add-dialogs/list moves; the state stays put.

#### B. Dialog-reset `useEffect` — MUST BE EXTENDED (current state, exact text)

Lines 204-220 (comment + effect):
```tsx
  // The three "Adicionar" dialogs above are only rendered inside the "Dados" tab's JSX, but their
  // open/draft state is hoisted here so it isn't lost across a tab round-trip. Because the dialogs
  // are unmounted (not just hidden) when the user leaves "Dados", a controlled `open={true}` left
  // over from before the switch would otherwise cause the dialog to reopen automatically — with
  // stale draft text — the moment the user navigates back to "Dados". Close and clear them whenever
  // the user is not on "Dados", mirroring the reset effect used for AdvogadosResponsaveisCard-style
  // sub-components below (see the `if (!editable) setModalOpen(false)` pattern).
  React.useEffect(() => {
    if (tab !== "dados") {
      setAddDocEntreModal(false);
      setAddDocATratarModal(false);
      setAddDeslocacaoModal(false);
      setNewDocEntre({ descricao: "", data: "" });
      setNewDocATratar({ descricao: "" });
      setNewDeslocacao({ descricao: "", local: "", data: "" });
    }
  }, [tab]);
```

**Required change per CONTEXT.md/UI-SPEC section 2:** split the single `tab !== "dados"` guard into per-dialog conditions since `documentosATratar`/`deslocacoes` dialogs move to their own tabs while `documentosEntregues` dialog stays in "dados" (Phase 79 scope):

```tsx
  React.useEffect(() => {
    if (tab !== "dados") {
      setAddDocEntreModal(false);
      setNewDocEntre({ descricao: "", data: "" });
    }
    if (tab !== "documentosATratar") {
      setAddDocATratarModal(false);
      setNewDocATratar({ descricao: "" });
    }
    if (tab !== "deslocacoes") {
      setAddDeslocacaoModal(false);
      setNewDeslocacao({ descricao: "", local: "", data: "" });
    }
  }, [tab]);
```
(Exact split shown for illustration — UI-SPEC explicitly leaves "one effect with per-dialog conditionals vs. split effects" as an executor discretion; either satisfies the contract as long as each dialog resets against its own tab, and `addDocEntreModal` keeps its existing `tab !== "dados"` condition untouched.)

The `TabKey` union already includes both target keys — no type change needed (lines 77-84):
```tsx
type TabKey =
  | "dados"
  | "contactosNotas"
  | "processos"
  | "pareceres"
  | "documentosEntregues"
  | "documentosATratar"
  | "deslocacoes";
```

#### C. "Documentos a Tratar" JSX block — CURRENT exact location and content (lines 890-939)

Currently nested inside the "Intake do Caso" `Card` at line 774, itself gated by `{isEditing ? ( ... ) : null}` (774...1018). See finding in section F below — this gate currently makes the whole section invisible in read mode; CONTEXT.md's "always visible in read mode" premise is not what today's code does (flagged as a discrepancy, see Section F).

```tsx
890	                {/* Documentos a Tratar */}
891	                <div className="space-y-2">
892	                  <div className="flex items-center justify-between">
893	                    <h4 className="text-sm font-medium text-neutral-700 dark:text-neutral-300">Documentos a Tratar</h4>
894	                    <Dialog open={addDocATratarModal} onOpenChange={setAddDocATratarModal}>
895	                      <DialogTrigger asChild>
896	                        <Button type="button" variant="outline" size="sm">Adicionar</Button>
897	                      </DialogTrigger>
898	                      <DialogContent>
899	                        <DialogHeader>
900	                          <DialogTitle>Adicionar Documento a Tratar</DialogTitle>
901	                        </DialogHeader>
902	                        <div className="space-y-4">
903	                          <div className="space-y-2">
904	                            <Label htmlFor="new-doc-tratar-descricao">Descrição</Label>
905	                            <Input
906	                              id="new-doc-tratar-descricao"
907	                              className="rounded-none"
908	                              value={newDocATratar.descricao}
909	                              onChange={(e) => setNewDocATratar({ descricao: e.target.value })}
910	                            />
911	                          </div>
912	                        </div>
913	                        <DialogFooter>
914	                          <Button type="button" variant="outline" onClick={() => setAddDocATratarModal(false)}>Cancelar</Button>
915	                          <Button type="button" onClick={confirmAddDocATratar}>Confirmar</Button>
916	                        </DialogFooter>
917	                      </DialogContent>
918	                    </Dialog>
919	                  </div>
920	                  {documentosATratar.length === 0 ? (
921	                    <p className="text-sm text-neutral-500 dark:text-neutral-400">Nenhum documento a tratar registado.</p>
922	                  ) : (
923	                    <ul className="space-y-1">
924	                      {documentosATratar.map((doc, index) => (
925	                        <li key={index} className="flex items-center justify-between border border-neutral-200 dark:border-neutral-800 px-3 py-2 text-sm">
926	                          <span>{doc.descricao}</span>
927	                          <button
928	                            type="button"
929	                            className="text-neutral-500 hover:text-red-600"
930	                            onClick={() => setDocumentosATratar((prev) => prev.filter((_, i) => i !== index))}
931	                            aria-label="Remover"
932	                          >
933	                            ✕
934	                          </button>
935	                        </li>
936	                      ))}
937	                    </ul>
938	                  )}
939	                </div>
```

Handler `confirmAddDocATratar` (lines 275-280, stays where it is — component-scope function, not JSX, not moved):
```tsx
  function confirmAddDocATratar() {
    if (!newDocATratar.descricao.trim()) return;
    setDocumentosATratar((prev) => [...prev, { ...newDocATratar }]);
    setNewDocATratar({ descricao: "" });
    setAddDocATratarModal(false);
  }
```

#### D. "Deslocações" JSX block — CURRENT exact location and content (lines 941-1013)

```tsx
941	                {/* Deslocações */}
942	                <div className="space-y-2">
943	                  <div className="flex items-center justify-between">
944	                    <h4 className="text-sm font-medium text-neutral-700 dark:text-neutral-300">Deslocações</h4>
945	                    <Dialog open={addDeslocacaoModal} onOpenChange={setAddDeslocacaoModal}>
946	                      <DialogTrigger asChild>
947	                        <Button type="button" variant="outline" size="sm">Adicionar</Button>
948	                      </DialogTrigger>
949	                      <DialogContent>
950	                        <DialogHeader>
951	                          <DialogTitle>Adicionar Deslocação</DialogTitle>
952	                        </DialogHeader>
953	                        <div className="space-y-4">
954	                          <div className="space-y-2">
955	                            <Label htmlFor="new-deslocacao-descricao">Descrição</Label>
956	                            <Input
957	                              id="new-deslocacao-descricao"
958	                              className="rounded-none"
959	                              value={newDeslocacao.descricao}
960	                              onChange={(e) => setNewDeslocacao((prev) => ({ ...prev, descricao: e.target.value }))}
961	                            />
962	                          </div>
963	                          <div className="space-y-2">
964	                            <Label htmlFor="new-deslocacao-local">Local</Label>
965	                            <Input
966	                              id="new-deslocacao-local"
967	                              className="rounded-none"
968	                              value={newDeslocacao.local}
969	                              onChange={(e) => setNewDeslocacao((prev) => ({ ...prev, local: e.target.value }))}
970	                            />
971	                          </div>
972	                          <div className="space-y-2">
973	                            <Label htmlFor="new-deslocacao-data">Data</Label>
974	                            <Input
975	                              id="new-deslocacao-data"
976	                              type="date"
977	                              className="rounded-none"
978	                              value={newDeslocacao.data}
979	                              onChange={(e) => setNewDeslocacao((prev) => ({ ...prev, data: e.target.value }))}
980	                            />
981	                          </div>
982	                        </div>
983	                        <DialogFooter>
984	                          <Button type="button" variant="outline" onClick={() => setAddDeslocacaoModal(false)}>Cancelar</Button>
985	                          <Button type="button" onClick={confirmAddDeslocacao}>Confirmar</Button>
986	                        </DialogFooter>
987	                      </DialogContent>
988	                    </Dialog>
989	                  </div>
990	                  {deslocacoes.length === 0 ? (
991	                    <p className="text-sm text-neutral-500 dark:text-neutral-400">Nenhuma deslocação registada.</p>
992	                  ) : (
993	                    <ul className="space-y-1">
994	                      {deslocacoes.map((d, index) => (
995	                        <li key={index} className="flex items-center justify-between border border-neutral-200 dark:border-neutral-800 px-3 py-2 text-sm">
996	                          <span>
997	                            {d.descricao}
998	                            {d.local ? ` (${d.local})` : ""}
999	                            {d.data ? ` — ${d.data}` : ""}
1000	                          </span>
1001	                          <button
1002	                            type="button"
1003	                            className="text-neutral-500 hover:text-red-600"
1004	                            onClick={() => setDeslocacoes((prev) => prev.filter((_, i) => i !== index))}
1005	                            aria-label="Remover"
1006	                          >
1007	                            ✕
1008	                          </button>
1009	                        </li>
1010	                      ))}
1011	                    </ul>
1012	                  )}
1013	                </div>
```

Handler `confirmAddDeslocacao` (lines 282-287, stays where it is):
```tsx
  function confirmAddDeslocacao() {
    if (!newDeslocacao.descricao.trim()) return;
    setDeslocacoes((prev) => [...prev, { ...newDeslocacao }]);
    setNewDeslocacao({ descricao: "", local: "", data: "" });
    setAddDeslocacaoModal(false);
  }
```

#### E. Placeholder branches to replace (current, lines 1078-1084)

```tsx
1078	          ) : tab === "documentosEntregues" ? (
1079	            <PlaceholderEmBreve />
1080	          ) : tab === "documentosATratar" ? (
1081	            <PlaceholderEmBreve />
1082	          ) : tab === "deslocacoes" ? (
1083	            <PlaceholderEmBreve />
1084	          ) : null}
```

`documentosEntregues` branch (1078-1079) is **out of scope** — untouched, stays `PlaceholderEmBreve` until Phase 79. Only the `documentosATratar` (1080-1081) and `deslocacoes` (1082-1083) branches are replaced.

**Analog for the wrapper shape** — sibling tab branches already render their own `Card`/`CardContent` (see `ClienteProcessosTab`, lines 1123-1192, and `ClienteParecerTab`, lines 1213+):
```tsx
function ClienteProcessosTab({ clienteId }: { clienteId: string }) {
  const processos = useProcessos({ cliente_id: clienteId });
  return (
    <Card>
      <CardContent className="p-0 bg-white dark:bg-[#020617]">
        {/* ... */}
      </CardContent>
    </Card>
  );
}
```
Per UI-SPEC section "Interaction Contract" #1, the target shape is:
```tsx
) : tab === "documentosATratar" ? (
  <Card>
    <CardContent className="space-y-2 pt-6">
      {/* relocated "Documentos a Tratar" block verbatim, lines 890-939 above */}
    </CardContent>
  </Card>
) : tab === "deslocacoes" ? (
  <Card>
    <CardContent className="space-y-2 pt-6">
      {/* relocated "Deslocações" block verbatim, lines 941-1013 above */}
    </CardContent>
  </Card>
) : null}
```
Exact wrapper spacing/padding classes (e.g. whether `CardContent` needs `pt-6` or inherits default padding) is executor's discretion per CONTEXT.md/UI-SPEC — "as long as visual output... is unchanged from today's 'Dados'-tab rendering." Note `CardContent`'s default padding already applies without an explicit className (see e.g. `ProcuracaoCard`'s or `Dados` card's own `<CardContent>` with no override at line 488) — a plain `<CardContent>` with no className override is the safer default to match today's un-styled block wrapping (today the two `<div className="space-y-2">` blocks sit directly inside the "Intake do Caso" `CardContent className="space-y-4"` at line 779, spaced by the parent's `space-y-4`; when isolated into their own `Card`, add back `space-y-2`... or similar spacing on the CardContent itself, or wrap in a `<div className="space-y-2">` as already authored — the existing per-section wrapper `<div className="space-y-2">` (891, 942) already carries the correct internal spacing and needs no change).

#### F. IMPORTANT DISCREPANCY — flag for planner/executor

CONTEXT.md and UI-SPEC both assert: *"Em modo leitura, a lista de itens já guardados mantém-se sempre visível... só o botão 'Adicionar' e o botão de remover (✕) ficam ocultos/inativos."*

**This is not what the current code does.** Direct inspection shows the entire "Intake do Caso" `Card` — which contains both "Documentos a Tratar" and "Deslocações" — is gated by a single `{isEditing ? ( ... ) : null}` at lines 774/1018:
```tsx
774	          {isEditing ? (
775	            <Card>
776	              <CardHeader>
777	                <CardTitle>Intake do Caso</CardTitle>
778	              </CardHeader>
779	              <CardContent className="space-y-4">
...
1015	                {serverError ? <p className="text-sm text-red-600">{serverError}</p> : null}
1016	              </CardContent>
1017	            </Card>
1018	          ) : null}
```
There is **no `else` branch** rendering a read-only view of `documentosATratar`/`deslocacoes` today — in read mode (`isEditing === false`), this entire section (including both lists) renders nothing at all, not even the list. The per-button `canEditClientes && editable` gating that CONTEXT.md describes ("o botão 'Adicionar' de ambas as listas continua gated por `canEditClientes && editable`") does not exist as an inline conditional either — access control is achieved transitively (only `canEditClientes` users can ever set `isEditing = true`, per line 403-406), not via a literal `canEditClientes && editable` expression wrapping the Adicionar buttons.

**Planner should resolve this discrepancy explicitly** — either:
1. Treat CONTEXT.md's read-mode-visible requirement as **new behavior to add in this phase** (would then NOT be "pure relocation, zero behavior change" as scoped), or
2. Treat it as a documentation inaccuracy in CONTEXT.md/UI-SPEC and preserve current behavior exactly (list hidden entirely outside edit mode, matching "Documentos Entregues" sibling section's identical gating) — this is the reading most consistent with the CONTEXT.md/UI-SPEC's own repeated emphasis on "sem alteração de comportamento" and "zero new visual surface."

Given the explicit phase boundary ("relocalização pura... sem alteração de comportamento") takes precedence over the narrative aside about read-mode visibility, the safer default is **(2): preserve today's exact `isEditing`-gated visibility, unchanged** — i.e. keep the relocated blocks wrapped in the same `{isEditing ? (...) : null}` condition inside their new tab branches, do not introduce a new always-visible read-mode list rendering. Flag this for explicit confirmation during planning/execution rather than silently resolving it either way.

---

## Shared Patterns

### Tab-branch → dedicated-component extraction pattern
**Source:** `ClienteProcessosTab` (lines 1123-1192), `ClienteParecerTab` (lines 1213+)
**Apply to:** both relocated blocks, if the executor chooses to extract them into named functions (`ClienteDocumentosATratarTab` / `ClienteDeslocacoesTab`) rather than inlining JSX directly in the ternary chain. Either approach (inline JSX in the ternary vs. extracted named component receiving props/state via closures or params) satisfies the UI-SPEC; inlining is simpler here since the relocated blocks depend on many local `useState`/handlers already declared at `ClienteDetailContent` scope (extracting to a separate function would require threading ~8 props through) — **inlining directly in the ternary (matching how "Documentos Entregues" currently renders) is the lower-risk choice** and keeps closures over `documentosATratar`/`newDocATratar`/etc. working without prop drilling.

### Dialog-reset-on-tab-change
**Source:** lines 211-220 (this same file, Phase 76 CR-01 fix)
**Apply to:** extend to cover `documentosATratar`/`deslocacoes` dialogs against their own new tab keys, per Section B above.

### `PlaceholderEmBreve` removal
**Source:** lines 1110-1120 (component definition, stays — still used by `documentosEntregues` branch and any other not-yet-built tab)
**Apply to:** only remove the two `<PlaceholderEmBreve />` call-sites at lines 1081/1083; do not delete the `PlaceholderEmBreve` function itself (still referenced at line 1079 for `documentosEntregues`).

---

## No Analog Found

None — this is a single-file, in-place relocation; every "pattern to copy" is already present in the same file (sibling tab branches, existing dialog-reset effect, existing `PlaceholderEmBreve` slots).

## Metadata

**Analog search scope:** `web/src/app/(dashboard)/clientes/[id]/page.tsx` only (per CONTEXT.md: "único ficheiro afetado")
**Files scanned:** 1
**Pattern extraction date:** 2026-07-06
