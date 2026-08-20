# Acknowledgements

This project is a port of **[coleam00/Archon](https://github.com/coleam00/Archon)**.

## Licence of the original

**MIT**, © 2025-2026 Cole Medin. Read from the `LICENSE` file at the root of the
repository at commit `d9364a3`, not from a badge.

## What was copied

**No source was copied.** No file, function, class or fragment of `coleam00/Archon`
appears in this project. Everything here is written against a behavioural
specification — `archon-port/specs/SPEC-001-archon.md` in the harness repository — and
the Java in `src/main` shares no text with the TypeScript it was derived from.

Three things did cross over, and none of them is source:

- **The behaviour itself.** How an unrecognised tool name is reported and why it can only
  ever be a warning, how a permission specifier is reduced to its base name, how an
  allow-list and a deny-list combine, how a model reference is classified, the order the
  configuration layers override each other in, which tier a missing tier falls back to,
  which layer each resolved value is attributed to, and which way a reasoning depth is
  clamped — all of these are derived from `coleam00/Archon` and reproduce it deliberately.
  That is what a port is, and it is not something to be coy about.
- **Two tables of names, reproduced as data.** The twenty-four audited Claude tool names
  and the four renamed ones in `Providers.java`, and the built-in tier presets per provider
  in `ModelProfile.java`, are the same values the original carries in
  `packages/providers/src/claude/capabilities.ts` and
  `packages/workflows/src/defaults/tier-defaults.json`. They are facts about other people's
  SDKs rather than expression, and reproducing them is the only way the two systems can be
  compared at all — but they were transcribed, so they are named here rather than left
  implied.
- **Scenario inputs.** `archon-port/bench/scenarios.json` in the harness repository holds
  the configurations and workflow nodes fed through both systems to compare their answers.
  They were written for that comparison; none is taken from the original's own test suite.

The probes and benchmark runners in the harness repository import and call
`coleam00/Archon` unmodified, from a clone kept beside the harness. They live there, not
here, and this project does not depend on it at build time or at run time.

## What that means for this project's licence

MIT is a permissive licence and imposes no share-alike obligation, so nothing about the
original constrains what this project may be licensed as. Its attribution clause applies to
redistributed copies of its own source, and none is included here; the attribution above is
given because it is owed to the work this was derived from, not because a copied file
forces it.

## Also used

- **[Akka](https://akka.io)** — the SDK and runtime this port is built on
  (`akka-javasdk` 3.6.3, Business Source License 1.1).
