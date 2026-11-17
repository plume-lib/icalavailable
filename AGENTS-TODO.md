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

- [ ] Experiment with the program (TZ, locale, file: URLs, stderr logging noise)
- [ ] Write harness `src/test/java/org/plumelib/icalavailable/ICalAvailableEndToEndTest.java`
      (subprocess, cwd = temp dir, env TZ/LC_ALL, optional env.txt, `-Pe2e.command`)
- [ ] Wire `-Pe2e.command` system property in build.gradle.kts
- [ ] Write `src/test/resources/end-to-end/README.txt`
- [ ] Test cases: basic busy event, no events, multiple days/weekend skipping,
      --days, --business-hours (multiple ranges, bad ranges, bad time),
      --timezone1, --timezone2 (aliases, printed names), unknown time zone,
      multiple calendars, recurring events (RRULE, EXDATE), all-day events,
      transparent events, overlapping events, events spanning business-hour
      boundaries, DST transition, date formats (yyyy/MM/dd, MM/dd/yyyy,
      MM/dd/yy), bad date, missing year, missing --iCal-URL, unrecognized
      args, unknown option, missing file, malformed file, --debug?
- [ ] Mention the tests in README.md
- [ ] Run `./gradlew spotlessApply build`; markdownlint-cli2 on Markdown
- [ ] Final report; delete AGENTS-TODO.md
