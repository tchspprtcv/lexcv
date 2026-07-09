# Deferred Items — Phase 87

## SpotBugs/FindSecBugs cannot run in this environment (pre-existing, out of scope)

**Discovered independently during:** Plan 87-02 Task 2 and Plan 87-03 Task 2 verification, both via
`mvn -f backend/pom.xml -q spotbugs:check` — same root cause, confirmed twice against different files.

**Issue:** `spotbugs-maven-plugin:4.8.3.1`'s bundled ASM class reader cannot parse "class file major
version 67" (Java 23 bytecode) and throws `IllegalArgumentException: Unsupported class file major
version 67`, terminating with `NoClassesFoundToAnalyzeException: No classes found to analyze` before
any project class is ever analyzed. The failure occurs while trying to load framework classes on the
classpath (e.g. `org.springframework.data.domain.Pageable`, `BCryptPasswordEncoder`, `CrudRepository`,
`io.jsonwebtoken.JwtParser`, `software.amazon.awssdk...S3Presigner$Builder`,
`org.springframework.validation.BindingResult`) — i.e. it cannot even load the pre-existing dependency
classpath, regardless of what application code is being analyzed. This is a tooling/JDK version
incompatibility between the pinned SpotBugs plugin version and the project's Java 23 target (`pom.xml`
sets `<java.version>23</java.version>`), not something introduced by either plan's code changes — it
fails identically regardless of which source file is being analyzed.

**Scope decision:** Out of scope for both plans — fixing this requires upgrading `spotbugs-maven-plugin`
to a Java-23-compatible release (or pinning a JDK 21 toolchain for the SAST step specifically), an
unrelated dependency/build-config change outside either plan's controller-notification-wiring scope.
Not auto-fixed per the Scope Boundary rule (pre-existing issue in unrelated configuration). Flagged as a
background task for dedicated follow-up (`task_e6adba53`).

**Verification substitute used instead:** `mvn -f backend/pom.xml -q -DskipTests compile` (passed for
all tasks in both plans) plus targeted `grep` checks against each task's stated acceptance criteria.
Functional/logic correctness was verified by code review against each plan's interfaces and pattern map,
not by static security analysis.

**Recommendation:** Track as milestone-level tech debt — bump `spotbugs-maven-plugin` to a version with
Java 23 (class file 67) support, verified independently of any feature phase. This means the project's
documented SAST gate (`mvn spotbugs:check` per CLAUDE.md) has likely never actually executed
successfully against Java 23 bytecode — worth confirming how long this has been broken.

## Pre-existing frontend lint errors/warnings (Plan 87-04, out of scope)

**Discovered during:** Plan 87-04, verification of Tasks 1-3 via `pnpm --dir web lint`.

**Issue:** `pnpm --dir web lint` reports 22 problems (5 errors, 17 warnings) both before and after
this plan's edits — an identical count confirmed by running lint immediately after Task 1's isolated
change and again after Tasks 2-3. Neither file this plan modified (`web/src/hooks/use-processos.ts`,
`web/src/app/(dashboard)/processos/[id]/page.tsx`) contributes any new entry; the only line attributed
to the modified page file is a pre-existing `'textareaClassName' is assigned a value but never used`
warning that predates this plan. The 5 errors are unrelated: `dashboard-shell.tsx` (`setState()` called
synchronously inside a `useEffect`) and `documentos/novo/page.tsx` (a ref passed to
`form.handleSubmit(...)` read during render).

**Scope decision:** Out of scope per the Scope Boundary rule — none of these files are in this plan's
`<files>` list, and none were touched by Tasks 1-3. Not auto-fixed.

**Recommendation:** Candidate for a small, dedicated cleanup pass in a future milestone (unused-var
removal in `processos/[id]/page.tsx`; effect/ref fixes in `dashboard-shell.tsx` and
`documentos/novo/page.tsx`). Not blocking for Phase 87.
