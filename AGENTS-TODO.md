# AGENTS-TODO: end-to-end tests for icalavailable

**This temporary plan file must be deleted before any pull request is merged.**

Goal: add data-driven end-to-end tests for plume-lib/icalavailable, in the
worktree `/home/mernst/java/plume-lib/icalavailable-fork-mernst-branch-e2e-tests`
(branch `e2e-tests`).  The tests are data files (inputs, args, expected
stdout/stderr/status) under `src/test/resources/end-to-end/`, one subdirectory
per test case, so that they can be run against another implementation in
another language.  A JUnit harness runs them against this implementation.
Model the format on
`/home/mernst/java/plume-lib/bibtex-clean-fork-mernst-branch-e2e-tests/src/test/resources/end-to-end/README.txt`
and its `BibtexCleanEndToEndTest.java` (read only; do not modify that repo).
Output must be deterministic: always pass `--date`, fix TZ and locale in the
environment, use local files (relative `file:` URLs), no network.
Do not change main source; record bugs as current behavior with a
description starting "BUG:".  Commit (including this file) on branch
`e2e-tests` at each milestone, with short descriptive messages; do not push,
create pull requests, change branches, or rewrite history.
`./gradlew build` must pass.  When every entry is done, delete this file and
commit the deletion.

## Checklist

Done so far: harness `ICalAvailableEndToEndTest.java`, `-Pe2e.command` in
build.gradle.kts, test-data README.txt, 59 test cases (all pass under
`./gradlew test`).  Generator scripts lived in the scratchpad and are not
needed; to regenerate a goal file, run the program as README.txt describes.

- [ ] Mention the tests in README.md
- [ ] Run `./gradlew spotlessApply build` (full build, including pmdTest,
      javadoc, Checker Framework); fix any problems
- [ ] markdownlint-cli2 on Markdown files
- [ ] Final report; delete AGENTS-TODO.md and commit the deletion
