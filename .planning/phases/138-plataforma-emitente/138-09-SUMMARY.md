# Plan 138-09 Summary: Office Settings Subscription Tab (SUBS-04)

## Status: COMPLETE

### Deliverables
1. **React Query Hooks (`web/src/hooks/use-minhas-subscricoes.ts`)**:
   - `useMinhasSubscricoes`: Fetches paginated subscription documents issued to the calling tenant via `/api/v1/faturacao/subscricoes`.
   - `useMinhaSubscricaoDetalhe`: Fetches detailed breakdown of a subscription document.
   - `useDescarregarMinhaSubscricaoPdf`: Trigger for direct PDF download.
   - `useDescarregarMinhaSubscricaoXml`: Trigger for direct signed XML download.
2. **Subscrição Tab Component (`web/src/app/(dashboard)/settings/subscricao-tab.tsx`)**:
   - Read-only information banner explaining DNRE compliance and platform invoice issuance.
   - Subscription summary card showing account holder, active plan, and active users count.
   - History table listing all subscription documents (FR and NC) with formatted dates, amounts, eFatura communication status badges, and PDF/XML download buttons.
3. **Settings Page Integration (`web/src/app/(dashboard)/settings/page.tsx`)**:
   - Added `Subscrição` tab guarded by `hasFinanceiroView` (`financeiro:view` authority).

### Verification
- `npx tsc --noEmit` passed with 0 errors.
- `npm test` passed 15/15 test suites (353 unit tests).
