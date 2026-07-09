# Deferred Items — Phase 87

## [Plan 87-02] SpotBugs/FindSecBugs cannot run in this environment (pre-existing, out of scope)

**Discovered during:** Task 2 verification (`mvn -f backend/pom.xml -q spotbugs:check`)

**Issue:** `spotbugs-maven-plugin:4.8.3.1` fails with `NoClassesFoundToAnalyzeException` because its
bundled ASM class reader cannot parse "class file major version 67" (Java 23 bytecode). The failure
occurs while trying to analyze framework classes on the classpath (e.g.
`org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder`, `CrudRepository`,
`io.jsonwebtoken.JwtParser`, `software.amazon.awssdk...S3Presigner$Builder`,
`org.springframework.validation.BindingResult`) — i.e. it cannot even load the pre-existing
dependency classpath, regardless of what application code is being analyzed. This is a tooling/JDK
version incompatibility between the pinned SpotBugs plugin version and the project's Java 23 target
(`pom.xml`), not something introduced by any code change in Plan 87-02.

**Scope decision:** Out of scope for this plan — fixing this requires upgrading `spotbugs-maven-plugin`
(an unrelated dependency/build-config change, potentially with its own compatibility ripple), which is
an architectural/tooling change outside a controller-notification-wiring task. Not auto-fixed per the
Scope Boundary rule (pre-existing issue in unrelated configuration).

**Verification substitute used instead:** `mvn -f backend/pom.xml -q -DskipTests compile` (passes for
all 3 tasks) plus targeted `grep` checks against each task's stated acceptance criteria. Functional/logic
correctness was verified by code review against the plan's interfaces and pattern map, not by static
security analysis.

**Recommendation:** Track as milestone-level tech debt — bump `spotbugs-maven-plugin` to a version with
Java 23 (class file 67) support, verified independently of any feature phase.
