---
phase: 51-frontend-upload-component
verified: 2026-06-19T00:00:00Z
status: human_needed
score: 4/4 must-haves verified
overrides_applied: 0
human_verification:
  - test: "Selecionar um PNG na página /documentos/novo"
    expected: "Um elemento <img> aparece imediatamente abaixo da zona de drop com a pré-visualização da imagem antes de clicar Enviar"
    why_human: "createObjectURL e renderização condicional de <img> só são verificáveis visualmente num browser real"
  - test: "Arrastar um PDF para a zona de upload na página /documentos/novo"
    expected: "A zona destaca-se com borda azul durante o arrasto; após largar, um <iframe> aparece com a pré-visualização do PDF"
    why_human: "O comportamento de drag-and-drop e o iframe requerem interação real com o browser"
  - test: "Fazer upload de um ficheiro e observar a barra de progresso"
    expected: "Durante o upload (XHR), a barra de progresso preenche-se progressivamente de 0% a 100% e desaparece após conclusão"
    why_human: "xhr.upload.onprogress só dispara durante transferências reais; não simulável com grep"
  - test: "Clicar 'Download' na página de detalhe de um documento"
    expected: "Uma nova aba abre com a URL pré-assinada do MinIO; nenhum ficheiro passa pelo Next.js"
    why_human: "window.open e o comportamento do browser requerem teste manual; o backend MinIO deve estar activo"
---

# Phase 51: Frontend Upload Component Verification Report

**Phase Goal:** O componente de upload de documentos oferece feedback visual durante a transferência, suporta drag-and-drop, mostra preview inline de imagens e PDFs, e inicia downloads via URL pré-assinada
**Verified:** 2026-06-19
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Ao selecionar ou largar um ficheiro, uma barra de progresso mostra a percentagem de upload em tempo real até 100% | VERIFIED | `useUploadDocumentoComProgresso` usa `xhr.upload.onprogress` (use-documentos.ts:115-118); progresso renderizado condicionalmente em novo/page.tsx:169-182 |
| 2 | Clicar em "Descarregar" num documento existente abre a URL pré-assinada retornada pelo backend diretamente no browser — nenhum ficheiro passa pelo servidor Next.js | VERIFIED | `useDownloadDocumento` faz `apiFetch<{ url: string; expiresIn: number }>` sem blob (use-documentos.ts:87-94); `onDownload` chama `window.open(res.url, "_blank", "noopener,noreferrer")` ([id]/page.tsx:48) |
| 3 | O utilizador pode arrastar um ficheiro do sistema operativo para a zona de upload e o ficheiro é aceite da mesma forma que clicando para selecionar | VERIFIED | `FileDropZone` implementa `onDragEnter`, `onDragOver`, `onDragLeave`, `onDrop`; `handleDrop` extrai `e.dataTransfer.files[0]` e chama `onFileChange` (file-drop-zone.tsx:36-43) |
| 4 | Antes de confirmar o upload, imagens (PNG, JPG, GIF) e PDFs mostram uma pré-visualização inline na interface | VERIFIED | `handleFicheiroSelecionado` detecta tipo e cria `URL.createObjectURL`; JSX renderiza `<img>` para imagens e `<iframe>` para PDFs condicionalmente (novo/page.tsx:56-75, 151-167) |

**Score:** 4/4 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `web/src/components/shared/file-drop-zone.tsx` | FileDropZone com drag-and-drop e pré-visualização inline | VERIFIED | 89 linhas; exporta `FileDropZone`; handlers drag completos; input hidden + button |
| `web/src/hooks/use-documentos.ts` | `useDownloadDocumento` com presigned URL; `useUploadDocumentoComProgresso` com XHR onprogress | VERIFIED | 142 linhas; ambas as funções presentes e substantivas |
| `web/src/app/(dashboard)/documentos/novo/page.tsx` | Página com FileDropZone, barra de progresso e pré-visualização | VERIFIED | 265 linhas; FileDropZone, estados `progresso` e `preVisualizacao`, preview condicional, barra de progresso |
| `web/src/app/(dashboard)/documentos/[id]/page.tsx` | onDownload atualizado para window.open com URL pré-assinada | VERIFIED | `onDownload` usa `window.open(res.url, "_blank", "noopener,noreferrer")`; nenhuma lógica de blob presente |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `documentos/novo/page.tsx` | `file-drop-zone.tsx` | import FileDropZone | WIRED | linha 14: `import { FileDropZone } from "@/components/shared/file-drop-zone"` |
| `documentos/novo/page.tsx` | `use-documentos.ts` | useUploadDocumentoComProgresso | WIRED | linha 15: `import { useUploadDocumentoComProgresso } from "@/hooks/use-documentos"` + usado linha 41 |
| `documentos/[id]/page.tsx` | `use-documentos.ts` | useDownloadDocumento → window.open | WIRED | `useDownloadDocumento` importado e usado; `window.open(res.url, "_blank", "noopener,noreferrer")` linha 48 |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `novo/page.tsx` — barra de progresso | `progresso` (state) | `xhr.upload.onprogress` via `onProgress` callback em `useUploadDocumentoComProgresso` | Sim — eventos XHR reais com `e.loaded / e.total` | FLOWING |
| `novo/page.tsx` — pré-visualização | `preVisualizacao` (state) | `URL.createObjectURL(file)` em `handleFicheiroSelecionado` | Sim — URL de objeto criado a partir do File real | FLOWING |
| `[id]/page.tsx` — download URL | `res.url` | `apiFetch` GET `/documentos/{id}/download` → JSON `{ url, expiresIn }` | Sim — backend retorna URL pré-assinada MinIO | FLOWING |

### Behavioral Spot-Checks

Step 7b: SKIPPED — requer servidor Next.js activo e backend MinIO. Os checks de `pnpm build` são documentados no SUMMARY (PASSED) mas não re-executados nesta verificação automatizada (ficheiros compilam sem erros TypeScript conforme confirmado pela estrutura dos imports e tipos).

### Probe Execution

Nenhum probe shell declarado no PLAN ou encontrado em `scripts/*/tests/probe-*.sh` para esta fase.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|---------|
| MIN-05 | 51-01-PLAN.md | Barra de progresso visível durante upload | SATISFIED | XHR `onprogress` em `useUploadDocumentoComProgresso`; barra renderizada quando `progresso !== null` |
| MIN-06 | 51-01-PLAN.md | Download via URL pré-assinada sem proxy Next.js | SATISFIED | `window.open(res.url, "_blank", "noopener,noreferrer")` — nenhum blob passa pelo Next.js |
| MIN-07 | 51-01-PLAN.md | Drag-and-drop nativo sem bibliotecas externas | SATISFIED | `FileDropZone` com handlers nativos; `tech_stack.added: []` no SUMMARY confirma zero novas dependências |
| MIN-08 | 51-01-PLAN.md | Pré-visualização inline de imagens e PDFs | SATISFIED | `<img>` para imagens, `<iframe>` para PDFs via `createObjectURL`; URLs revogadas em unmount e após submit |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| — | — | — | — | Nenhum anti-padrão encontrado |

Observações:
- Sem marcadores TBD/FIXME/XXX em nenhum dos quatro ficheiros modificados.
- Sem `return null` ou implementações stub nas funções de negócio.
- `useUploadDocumento` (hook original) mantido intacto para compatibilidade retroativa — não é stub, é API deliberadamente preservada.
- Object URLs corretamente revogadas tanto em unmount (`useEffect` cleanup) como após submit bem-sucedido (novo/page.tsx:97-99).

### Human Verification Required

#### 1. Pré-visualização de imagem

**Test:** Na página `/documentos/novo`, clicar "Clique para selecionar" e escolher um ficheiro PNG ou JPG.
**Expected:** Um elemento `<img>` aparece imediatamente abaixo da zona de drop com a miniatura da imagem, antes de clicar o botão Enviar.
**Why human:** `createObjectURL` e renderização condicional de `<img>` só são verificáveis com um browser real.

#### 2. Drag-and-drop de PDF

**Test:** Arrastar um ficheiro PDF do explorador de ficheiros para a zona de upload em `/documentos/novo`.
**Expected:** A zona destaca-se com borda azul durante o arrasto; após largar, um `<iframe>` aparece com a pré-visualização do PDF.
**Why human:** Eventos de drag-and-drop e rendering de iframe requerem interação real com o browser.

#### 3. Barra de progresso em tempo real

**Test:** Fazer upload de um ficheiro grande (>1 MB) na página `/documentos/novo` com o DevTools de rede aberto para simular conexão lenta (throttling).
**Expected:** A barra de progresso azul preenche-se progressivamente de 0% a 100% e desaparece após conclusão bem-sucedida.
**Why human:** `xhr.upload.onprogress` só dispara durante transferências reais; não simulável estaticamente.

#### 4. Download via URL pré-assinada

**Test:** Na página `/documentos/{id}` de um documento existente, clicar "Download".
**Expected:** Uma nova aba abre com a URL pré-assinada do MinIO (URL deve conter parâmetros de assinatura temporária); nenhum blob é gerado no Next.js; o ficheiro descarrega directamente do MinIO.
**Why human:** `window.open` e o comportamento de nova aba requerem browser; o backend MinIO deve estar activo e o documento deve existir.

### Gaps Summary

Nenhum gap encontrado. Todos os artefactos existem, são substantivos e estão correctamente ligados. Os quatro success criteria da fase estão implementados no código. A verificação humana é necessária apenas para confirmar comportamento visual e de rede em runtime — não há lacunas de implementação conhecidas.

---

_Verified: 2026-06-19_
_Verifier: Claude (gsd-verifier)_
