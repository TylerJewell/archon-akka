# archon-akka

Decides which tools an AI coding agent is allowed to use on a step of a workflow, and which
model that step runs on.

A port of [coleam00/Archon](https://github.com/coleam00/Archon) onto **Akka**, built with
**Akka Specify**.

---

## Where it came from

Archon runs software-development work as workflows: you write the steps down in a file, and
an AI agent fills in the thinking at each step while the shape of the run stays fixed. Two
of its settings decide how repeatable a run actually is — the list of tools a step may use,
and the name of the model it runs on — and this port rebuilds those two decisions and
nothing else. The port is the vehicle; the specification is the deliverable.

The specification this port was generated from is in
[TylerJewell/specify](https://github.com/TylerJewell/specify) under `archon-port/`.

---

## coleam00/Archon → this port

📉 437 TypeScript lines → **787 Java lines**<br>
📁 8 files → **15 files**<br>
🧪 0 tests over this behaviour → **30 tests**<br>
⚡ 0.0014 → **0.0039** milliseconds, one model-pinning decision<br>
⚡ 0.3688 → **0.0173** milliseconds, one tool-permission decision<br>
🎯 31 questions asked of both → **31 answered the same**

Full method and the numbers that did *not* make this list:
[`bench/REPORT.md`](https://github.com/TylerJewell/specify/blob/main/archon-port/bench/REPORT.md).

---

## What it took to build

⏱️ **1.1 hours** from the first command to the published repository, **1.1** of them active<br>
💬 **393** exchanges with the model<br>
✍️ **367,222** tokens written by the model, **100,094,250** counting everything sent and re-sent<br>
🙋 **0** questions to a human<br>
🧪 **30** tests

```bash
python toolkit/tokens.py --port archon    # turns, tokens, elapsed and active time
```

The record of every question, and where the time went, is in
[`port-log/`](https://github.com/TylerJewell/specify/tree/main/port-log).

---

## What it does

From the specification:

- **A tool name the agent's software does not recognise is reported and then ignored.** The
  layer underneath matches tool names as plain text, so a misspelled name quietly does
  nothing at run time; saying so is the only protection there is, and refusing the whole
  workflow over it would break workflows that are correct.
- **A tool name that was renamed says what it was renamed to.** Four names changed meaning
  in a recent release of the agent software, and an old one now takes tools away from
  nobody.
- **An empty list of allowed tools means no tools, and no list at all means the usual
  set.** These look alike and are opposite: reading the first as the second hands an agent
  everything the author was trying to take away.
- **A model name is one of three things, decided by how it is written.** `small`, `medium`
  and `large` are sizes; a name starting with `@` is a nickname somebody defined; anything
  else is passed straight through to the provider.
- **A size nobody configured settles for its nearest neighbour, and says which one it
  took.** Asking for the largest model on a machine that only has the smallest gets the
  smallest rather than an error.
- **Every resolved value says where it came from.** The step itself, the workflow around
  it, a nickname three configuration layers down, or nowhere at all — and "nowhere" is a
  real answer that is never confused with a default.
- **A nickname that points at a different provider wins, and the disagreement is
  reported.** A step can say it wants one company's agent while a nickname points at
  another's; the nickname decides, and nobody has to find that out by reading the logs.
- **When a model cannot think as deeply as asked, it steps down rather than up.** A
  request for deep thinking on a model that does not offer it settles for shallower rather
  than buying more than was asked for.

---

## Design decisions

**Pure functions.** Nothing here reads a clock, a file, or a setting from the machine it
runs on, so two callers asking the same question always get the same answer. That means a
run can be replayed later from what was sent, without hoping the machine has not changed
underneath.

**Configuration travels in the request.** Every layer of settings — the ones shipped with
the software, the ones for the machine, the ones for the project, the ones for the person —
arrives with the question instead of being read from disk. A caller who keeps the request
keeps everything needed to explain the answer.

**Findings carry a level, never a decision.** The service says a tool name will be ignored
and how bad that is, and stops there rather than refusing the workflow. Whoever called gets
to decide whether an ignored name is worth stopping for, because only they know what the
run is for.

**Absent is a value.** A model nobody chose is recorded as "nobody chose one" rather than
being filled in with a sensible guess. Anything reading the answer can tell the difference
between a choice and a gap, which is exactly the difference that decides whether a run is
pinned.

**The answers use the original's own words.** Where the original calls something
`model ref` or `warning`, this does too, rather than inventing tidier names. Two systems
answering the same question in the same words can be compared directly, and a translation
step in between is a place a real disagreement can hide.

---

## Running it — the short path

You do not need Java, Maven, or the Akka CLI installed. Akka Specify installs them for you.

**1. Install Akka Specify** in Claude Code:

```
/plugin marketplace add akka/ai-marketplace
/plugin install akka@akka-ai-marketplace
```

Restart Claude Code when it asks.

**2. Give it this prompt:**

> Clone https://github.com/TylerJewell/archon-akka into a new directory and open it.
> Then run /akka:setup to install everything this project needs, and /akka:build to
> compile it, run the tests, and start it locally.

**3. Ask it a question:**

```bash
curl -s -X POST http://localhost:9020/determinism/tools \
  -H 'Content-Type: application/json' \
  -d '{"provider":"claude","allowedTools":["Reed","Task","Bash(git:*)"],"deniedTools":null}'
```

---

## Running it — the developer path

### Requirements

- Java 21 or newer
- Maven 3.9 or newer
- An Akka download token — run `akka code token` once

No key for any model provider is needed. This service decides which model a run would use;
it never calls one.

### Start the service

```bash
mvn compile
akka local run
```

The service starts on **port 9020**.

### Run the tests

```bash
mvn verify
```

`mvn test` alone runs 24 of the 30 tests. The other six start a runtime and run in the
`verify` step.

---

## What you can ask it

| Ask | What comes back |
|---|---|
| `POST /determinism/tools` with a provider and the two tool lists | every finding about the names, the effective set of tools, and which names the provider does not know |
| `POST /determinism/pin` with the configuration layers, a workflow and a step | the provider, model and thinking depth the step would run at, and where each of the three came from |
| `POST /determinism/effort` with a provider and a thinking depth | whether that depth reaches that provider, and where it lands inside what the provider offers |

---

## Configuration

| Variable | Default | Notes |
|---|---|---|
| none | — | every input arrives in the request; the service holds nothing between calls |

---

## Where it differs from coleam00/Archon

Everything not listed here behaves the same way on purpose, including the parts that look
like mistakes.

- **Settings arrive with the question.** Archon builds its list of model nicknames by
  reading files on the machine and rows from a database every time a run starts. This port
  takes all of that in the request, because a decision that reads its own inputs cannot be
  replayed later by anyone holding a copy of the question.
- **Nothing remembers what a size meant last time.** In Archon, a workflow pinned to
  `large` runs on whatever `large` names today; the same is true here. Neither system keeps
  a record of what a previous run resolved to, so a run is repeatable only against a stated
  set of settings. This port makes that set an explicit input rather than something read
  from the machine, which is as close to pinning as either gets.
- **The answer says which size actually matched.** When a size falls back to its neighbour,
  Archon works out which neighbour matched and leaves it to each caller whether to mention
  it. This port always returns it, so a caller that wants to say "you asked for the largest
  and got the smallest" can, and one that does not can ignore it.
- **Findings are handed over, not acted on.** Archon prints its findings on the command
  line or sends them as a chat message, and carries on either way. This port returns them
  and carries on in exactly the same way, leaving any decision to stop to whoever called —
  a service cannot know whether an ignored tool name matters to the run it is part of.
- **A provider nobody has heard of and a provider that cannot think deeply give the same
  answer.** In Archon both come back as "thinking depth does not reach this one", and the
  two are indistinguishable. This port reproduces that exactly rather than inventing a
  third answer, because inventing one would mean the two systems disagree about a case
  neither of them can actually tell apart.
- **One tool name is added quietly, and this was never run.** Archon adds the ability to
  invoke a skill to a step's allowed list, but only when that step names at least one skill.
  This port does the same, from reading the original's code — reaching that line needs a
  live session with the agent software, so **not checked** by running it.
- **The thinking-depth vocabularies of two providers were read, not run.** The lists for
  two of the five providers are private to the file that uses them and could not be
  imported into the comparison, so this port carries them on the strength of reading the
  original. The other two were compared directly and agree. **Not checked** for those two.
- **The tool-permission arithmetic is compared against one provider only.** Archon works
  out the final tool list separately inside each provider's own integration; only one of
  them exposes it as something that can be called on its own. This port implements one rule
  for every provider, matching the one that could be compared. For the others, **not
  checked**.
- **Nothing here runs an agent.** Archon decides these things a moment before dispatching
  the work. This port only decides. That is scope rather than a difference in behaviour,
  and it is stated here because a reader arriving from Archon would otherwise expect a
  workflow to run.

---

## Licence

coleam00/Archon is MIT, © 2025-2026 Cole Medin. This port reimplements the behaviour
without copied source; see [`ACKNOWLEDGEMENTS.md`](ACKNOWLEDGEMENTS.md).
