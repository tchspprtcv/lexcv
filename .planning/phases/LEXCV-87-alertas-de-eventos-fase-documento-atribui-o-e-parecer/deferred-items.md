# Deferred Items — Phase 87

## [87-03] SpotBugs cannot run against Java 23 bytecode (pre-existing environment limitation)

**Found during:** Task 2 (`mvn -f backend/pom.xml -q spotbugs:check`)

**Issue:** `spotbugs-maven-plugin` 4.8.3.1's bundled ASM class reader throws
`IllegalArgumentException: Unsupported class file major version 67` for every class it attempts
to load — including completely unrelated framework classes never touched by this plan
(`org.springframework.data.domain.Pageable`, `org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder`,
`org.springframework.data.repository.CrudRepository`, `io.jsonwebtoken.JwtParser`,
`software.amazon.awssdk.services.s3.presigner.S3Presigner$Builder`,
`org.springframework.validation.BindingResult`). The run terminates with
`edu.umd.cs.findbugs.NoClassesFoundToAnalyzeException: No classes found to analyze` before any
project class — including `ParecerController.java` — is ever analyzed.

**Root cause:** `pom.xml` sets `<java.version>23</java.version>` (class file major version 67).
`spotbugs-maven-plugin` 4.8.3.1 predates full Java 23 bytecode support in its bundled ASM
dependency. This is an environment/tooling version mismatch, not a defect in any plan's code
changes — it fails identically regardless of what source file is being analyzed, since it can't
even parse the JDK/Spring/AWS SDK classes on the classpath.

**Scope decision:** Out of scope for Plan 87-03 per the executor's SCOPE BOUNDARY rule — the
failure is not caused by this plan's 2-line diff to `ParecerController.java`, and fixing it would
require either bumping `spotbugs-maven-plugin` to a Java-23-compatible release or lowering
`java.version`, both of which are project-wide tooling/infrastructure changes outside this
plan's `<files>` scope. Not fixed here.

**Verification performed instead:** `mvn -f backend/pom.xml -q -DskipTests compile` (passed,
silent/zero output) + `grep` confirming both `notificarParecerAtribuido(` call sites exist at
the correct splice points with no new `advogadoId`-previous-value read introduced.

**Status:** deferred — candidate for a future milestone's tooling-upgrade housekeeping (bump
`spotbugs-maven-plugin` version or pin a JDK 21 toolchain for the SAST step specifically).
