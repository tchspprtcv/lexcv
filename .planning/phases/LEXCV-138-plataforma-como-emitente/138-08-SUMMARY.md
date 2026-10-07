# Plan 138-08 Summary: Platform Billing Console UI and Hooks

## Status: COMPLETE

### Deliverables
1. **TypeScript Types (`web/src/types/platform-faturacao.ts`)**:
   - `PlatformConfigFiscal`, `PlatformConfigFiscalInput`, `CriarSerieInput`, `RegistarPagamentoSubscricaoInput`, `EmitirNotaCreditoSubscricaoInput`.
2. **React Query Hooks (`web/src/hooks/use-platform-faturacao.ts`)**:
   - `usePlatformConfigFiscal`, `useSavePlatformConfigFiscal`, `useCreatePlatformSerie`, `useRegistarPagamentoSubscricao`, `useEmitirNotaCreditoSubscricao`, `usePlatformDocumentos`, `useDescarregarPlatformPdf`, `useDescarregarPlatformXml`.
   - Uses `PLATAFORMA_ADMIN` role guards.
3. **UI Components (`web/src/app/(dashboard)/plataforma/faturacao/`)**:
   - `page.tsx`: Protected billing dashboard with metrics, quick actions, configuration card, and subscription documents table.
   - `config-fiscal-card.tsx`: Card for viewing/updating LexCV NIF, certificates, eFatura environment (SIMULADO / PRODUCAO), and active series.
   - `registar-pagamento-dialog.tsx`: Form dialog to manually register subscription bank transfer/cash payments and trigger automatic invoice generation.
   - `emitir-nc-dialog.tsx`: Form dialog to issue credit notes referencing previously issued subscription invoices with justification and line rectifications.
   - `documentos-table.tsx`: Filterable/paginated table listing all issued platform subscription documents (FR / NC) with status badges and PDF/XML download triggers.
4. **Navigation Integration (`web/src/app/(dashboard)/plataforma/page.tsx`)**:
   - Added direct navigation button to `/plataforma/faturacao`.

### Verification
- `npx tsc --noEmit` passed with 0 errors.
- `npm test` passed 15/15 test suites (353 unit tests).
