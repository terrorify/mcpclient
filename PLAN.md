# mcpclient — MCP-bridged Fabric debug client: the e2e instrument for the whole stack

A client-side Fabric mod plus a Python harness that turns a **vanilla-physics** Minecraft
client into a fully scriptable, fully observable probe of the entire Terrorify network path:

```
client(mcpclient) → TCPShield edge → internode (regional node, limbo/auth, ViaLite?) → stomzy backend
                                                                                          │
harness ←──────────── SpacetimeDB (hot-* worlds/state, coldmain accounts/stats) ──────────┘
```

Claude drives it over MCP: read tick-stamped client truth, drive deterministic inputs, tap
every inbound/outbound packet with sizes and timestamps, and diff all of it against server
authority traces and SpacetimeDB state. One tool, four jobs:

1. **Movement authority e2e** — flatten ALL false flags (the capture-replay harness).
2. **Perception authority e2e** — prove PVS culling + fake-health from the only vantage
   point that matters: what the client actually received.
3. **Network efficiency** — measure how well we stream to the client (chunk bytes,
   time-to-playable, RTT per hop, transfer handoff cost) and compare algorithms.
4. **Full-path integration tests** — login/auth/limbo/CTransfer flows asserted end to end,
   including SpacetimeDB side effects.

## Reference projects (researched 2026-07-27)

| Project | License | Use |
|---|---|---|
| cuspymd/mcp-server-mod | CC0 | Embedded streamable-HTTP MCP server pattern (Java, localhost). |
| MCCTP + MCSTP (lucasoyen) | MIT | Off-thread Netty telemetry + per-tick rich snapshot shape. |
| ControlCommands | MIT | Held-input-as-command pattern; we do tick-scripted KeyBinding actuation instead. |
| yoraudev/minecraft-mcp | custom restrictive | Tool-surface reference ONLY — never copy code. |

Fresh mod (none target 26.1.1; our needs are a superset). Namespace
`dev.terrorify.tools.mcpclient`, mod id `mcpclient`. **MC 26.1.1 pinned** to match live
backends — with `--backend-proto 775` internode bypasses ViaLite for us, and a config flag
lets us *force* the ViaLite path (connect masquerading as 26.1.2) to regression-test the
translator specifically. **Vanilla physics untouched**: observation + input actuation only.

## Project structure

```
tools/mcpclient/
├── PLAN.md
├── mod/                                  # the Fabric mod (Java 25, loom, own Gradle — never in images)
│   ├── build.gradle.kts / gradle.properties / settings.gradle.kts
│   └── src/main/java/dev/terrorify/tools/mcpclient/
│       ├── McpClientMod.java             # ClientModInitializer; wires everything
│       ├── mcp/
│       │   ├── McpHttpServer.java        # streamable-HTTP MCP on 127.0.0.1:47800, own thread
│       │   └── tools/                    # one class per MCP tool, registry-driven
│       ├── telemetry/
│       │   ├── TickRecord.java           # immutable per-tick snapshot (schema below)
│       │   ├── TelemetryBuffer.java      # ring buffer, 24k ticks, END_CLIENT_TICK sampler
│       │   └── MovePacketTap.java        # outgoing C→S position/flags — the bytes internode sees
│       ├── net/
│       │   ├── InboundPacketTap.java     # every clientbound packet: type, size, nanotime, tick
│       │   ├── RouteProbe.java           # DNS/SRV resolution, connected endpoint, server brand
│       │   │                             #   (detects TCPShield edge + ViaLite-vs-native path)
│       │   ├── RttMeter.java             # keepalive/ping RTT series; clock-offset estimation
│       │   ├── ChunkStreamMeter.java     # per-chunk: bytes, section count, recv time, distance;
│       │   │                             #   time-to-first-chunk, time-to-playable, resend/rewrite counts
│       │   └── TransferTracer.java       # CTransfer/Transfer packets: hop timeline, reconnect cost,
│       │                                 #   state carried across regional-node handoffs
│       ├── perception/
│       │   ├── EntityLedger.java         # every spawn/despawn/move/meta/health event received,
│       │   │                             #   tick-stamped, per entity — the client-side truth of PVS
│       │   └── HealthProbe.java          # observed health values per entity (fake-health assertion)
│       ├── world/
│       │   └── WorldProbe.java           # get_block/get_area/palette-dump of client chunks
│       ├── input/
│       │   └── InputScriptEngine.java    # per-tick key press/release + look targets via KeyBinding
│       └── capture/
│           └── CaptureWriter.java        # JSONL streams: ticks, inbound, outbound, entities, chunks
├── harness/                              # Python, runs on the workstation; talks MCP to the mod
│   ├── mcpc.py                           # thin MCP client wrapper (connect, tools, capture mgmt)
│   ├── serverfeeds.py                    # server-side truth: pod log tap (kubectl logs -f),
│   │                                     #   /debug movement JSONL feed, [authority-native] lines
│   ├── spacetime.py                      # HTTP SQL reads vs hot-*/coldmain (session rows, stats,
│   │                                     #   world blobs) — asserts DB side effects of a scenario
│   ├── align.py                          # tick/packet alignment: client capture ↔ server trace
│   │                                     #   (position-sequence correlation + RTT clock offset)
│   ├── reports/
│   │   ├── movement_diff.py              # per-tick client vs recv vs validated pos; flags ±10 ticks ctx
│   │   ├── perception_report.py          # PVS matrix vs EntityLedger: leaks, wrong-hides, show-latency
│   │   ├── net_report.py                 # bandwidth by packet family, chunk streaming curves,
│   │   │                                 #   RTT/jitter per phase, transfer handoff timelines
│   │   └── compare_runs.py               # A/B two captures (algorithm/tuning comparisons)
│   └── scenarios/                        # each = connect + script + capture + assert; exit code = pass
│       ├── _base.py                      # scenario runner: spin up feeds, run, collect, report
│       ├── login_premium.py              # TCPShield → nae-premium → backend; auth timing budget
│       ├── login_cracked.py              # limbo staged auth (/register, chat password, /login-confirm)
│       ├── transfer_regional.py          # CTransfer hop (premium→cracked node or region), state integrity
│       ├── vialite_translated.py         # force 26.1.2 path; assert byte-identical movement semantics
│       ├── movement_stair_plaza.py       # THE zero-flag regression: scripted sprint-jump circuit
│       ├── movement_matrix.py            # slab landings, bow-walk, stair descent, standstill settle
│       ├── chunk_streaming.py            # join + sprint-far: time-to-playable, bytes/chunk, ordering
│       ├── perception_cover.py           # 2 clients: one behind deep cover — assert NO entity packets
│       ├── perception_emerge.py          # cover→open: show-latency ≤ budget; never-hide-visible check
│       └── perception_fakehealth.py      # damage a subject; assert observed health is always fake
├── fixtures/                             # committed golden captures for offline regression (small)
└── captures/                             # .gitignored run output
```

### TickRecord schema (telemetry core)

```json
{"tick":81234,"wall_ns":123,"pos":[9.31,126.0,1.44],"vel":[0.0,-0.0784,0.21],
 "on_ground":true,"yaw":143.2,"pitch":12.0,"pose":"STANDING","sprinting":true,
 "bb":[...6],"block_below":"minecraft:jungle_planks[...]","block_feet":"minecraft:air",
 "input":{"fwd":1,"strafe":0,"jump":true,"sprint":true},"last_teleport_id":17}
```

Companion streams (same capture, separate JSONL files): `outbound.jsonl` (move-packet tap),
`inbound.jsonl` (type/size/ns — sampled or full), `entities.jsonl` (EntityLedger events),
`chunks.jsonl` (ChunkStreamMeter). All share the tick counter + nanotime for alignment.

## MCP tool surface

Read: `get_state`, `get_ticks{from,to}`, `get_block{x,y,z}`, `get_area{min,max}`,
`get_entities` (ledger snapshot: which entities the client currently knows + last health),
`get_net_stats` (RTT series, bandwidth by family, chunk metrics), `get_route`
(resolved entry, endpoint, brand string, ViaLite-or-native), `screenshot`.

Act: `connect{host,port?,username?,force_via?}` / `disconnect` (harness controls the
session — required for login/transfer scenarios), `run_script{steps:[{ticks,keys,yaw?,pitch?}]}`,
`send_chat{text}`, `stop`.

Capture: `capture_start{label,streams:[...]}` / `capture_stop`.

## How each stack layer gets e2e coverage

**TCPShield / DNS entry** — RouteProbe records which SRV/edge resolved and connect
latency; login scenarios run against `play.minemares.com` (real path) vs direct node IP,
and net_report diffs edge overhead. Catches silent routing regressions
(tcpshield_config wipes, public_entry gaps) as failing connect assertions.

**internode (limbo, auth, transfer, ViaLite)** — login_cracked drives the full staged
auth dialog with send_chat and asserts timing + final spawn; transfer_regional walks a
CTransfer and asserts no state loss and bounded reconnect cost (TransferTracer);
vialite_translated A/Bs the translator against native — the exact test that would have
caught the Y-corruption in an afternoon instead of eleven releases. The proxy's
MovementSetback/teleport-confirm absorption (two-authority fight) becomes directly
observable: the mod logs every S→C teleport + confirm, the server trace shows what the
backend believed — align.py exposes any packet internode ate.

**stomzy backend / movement authority** — movement_stair_plaza + movement_matrix are the
zero-flag regression gates run after every release; movement_diff.py isolates any client
vs recv vs validated divergence per tick (closes the +1 mystery: outbound tap Y vs
server-logged Y, plus get_block at (9,125,1) settles client-world-vs-palette). Server side
needs one small follow-up: a JSONL sink for the `/debug movement` feed (machine-readable
accepted-move trace with server tick + received pos).

**perception authority (PVS culling + fake health)** — the harness runs TWO mod clients
(mod supports multiple instances via `--port` offset): a viewer and a subject.
perception_cover scripts the subject into deep cover and asserts the viewer's EntityLedger
received zero spawn/move packets for it (deep-cover ESP defeated — provable, not assumed);
perception_emerge measures show-latency on exit from cover against the "never hide a
visible player" budget and catches false negatives; perception_fakehealth damages the
subject and asserts the viewer never observed a real health value. perception_report.py
also computes bandwidth saved by culling (entity-packet bytes with PVS on vs off) — the
optimization/algorithm-comparison loop: bake two PVS variants, run the same scripted
fight, compare_runs.py scores leak-count, show-latency, and bytes.

**SpacetimeDB (hot/cold)** — spacetime.py reads via HTTP SQL after each scenario:
session rows created/released (ghost-session regression), stats landed in cold, world
source consistency vs what ChunkStreamMeter saw. Scenarios fail if the DB side effect is
missing — e2e means the database too.

**network efficiency & algorithms** — ChunkStreamMeter + InboundPacketTap give the
baseline curves (time-to-playable, bytes/chunk, packet-family bandwidth, RTT/jitter under
load). Any streaming change (chunk send ordering, view-distance tuning, perception
culling, packet batching in internode) gets an A/B with compare_runs.py on identical
scripted runs — same inputs, same world, measured deltas.

## Milestones

- **M0 scaffold** — loom project joins live 26.1.1; `connect`/`disconnect`/`send_chat` +
  MCP server up. (Offline-auth capable for the cracked path.)
- **M1 movement truth** — TelemetryBuffer, MovePacketTap, world probes, capture, align.py,
  movement_diff.py. *Payoff: +1 mystery and stair-mirror decided; v0.4.14 verified by script.*
- **M2 input scripting + regression gate** — InputScriptEngine, movement_stair_plaza as a
  pass/fail scenario, server JSONL debug sink.
- **M3 network layer** — InboundPacketTap, RouteProbe, RttMeter, ChunkStreamMeter,
  TransferTracer, net_report; login/transfer/vialite scenarios.
- **M4 perception** — EntityLedger, HealthProbe, two-client harness, the three perception
  scenarios + perception_report. Lands alongside PerceptionAuthority implementation so the
  PVS ships with its e2e proof from day one.
- **M5 optimization loop** — compare_runs A/B tooling, fixtures/golden captures, wire the
  scenario suite into the release checklist.

## Defaults chosen (flag if wrong)

- MCP HTTP pull with lossless tick history (no 20Hz WS push; a live dashboard can bolt on later).
- Port 47800 (+N per extra client instance).
- Inbound tap records type+size for everything, full decode only for movement/teleport/
  entity/chunk families (keeps overhead ~zero; vanilla physics and frame times unaffected).
- Two-client perception testing over faking a second player server-side — real packets or
  it isn't e2e.

## Status (2026-07-27)

**Built and verified today, scoped to movement authority only** (perception/M4,
net layer M3 beyond the move-packet tap, and M5 explicitly deferred per
direction to focus on movement debugging):

- Fabric mod compiles and runs against real MC 26.1.1 (Mojmap names, confirmed
  by inspecting the actual client jar — no Yarn mappings exist for this
  version; loom needs no `mappings` dependency at all).
- MCP streamable-HTTP server (JSON-RPC: `initialize`/`tools/list`/`tools/call`)
  on `127.0.0.1:47800/mcp`, verified live via curl.
- Tools: `get_state`, `get_ticks`, `get_block`, `get_area`, `connect`,
  `disconnect`, `send_chat`, `run_script`, `stop`, `capture_start`,
  `capture_stop`. `get_ticks`/`get_area` are capped and windowed by default
  (300 ticks / 4096 blocks) to keep an MCP-driven loop context-cheap; bulk
  analysis reads the JSONL capture files directly instead of paging the tool.
- TelemetryBuffer (per-tick pos/vel/on_ground/yaw/pitch/pose/sprinting/bb/
  block_below/block_feet/input/last_teleport_id), MovePacketTap (outbound
  `ServerboundMovePlayerPacket` via a `Connection.send` mixin) and the inbound
  `ClientboundPlayerPositionPacket` tap (`Connection.channelRead0` mixin) —
  the exact client-vs-server movement diff PLAN.md's item 1 asks for.
- `InputScriptEngine` (tick-scripted key/look actuation) and `CaptureWriter`
  (JSONL: ticks/outbound/inbound) with a documented zero-lag tick-ordering
  contract (script advances on START_CLIENT_TICK, telemetry samples on
  END_CLIENT_TICK).
- Python harness: `mcpc.py` (stdlib-only MCP client), `align.py` (tick join),
  `reports/movement_diff.py` (flags server corrections beyond a threshold,
  ±10-tick context), `scenarios/movement_stair_plaza.py`.
- End-to-end proof: ran a real local vanilla 26.1.1 server, connected via the
  `connect` tool, ran `movement_stair_plaza.py` — captured 100+ real ticks,
  real outbound movement packets, correctly reported zero false corrections
  (exit 0). Deployed `mcpclient-1.1.0.jar` + `fabric-api-0.145.4+26.1.1.jar`
  to the local ModrinthApp `mcpclient` profile's `mods/` folder.

**v1.1.0 — live server-authority debug bridge via SpacetimeDB** (per direction:
"use spacetime between client and server to determine whether the debugging
mod is active and communicate between client and server"):

- New hot-module tables/reducers (`spacetime/hot/src/{tables,reducers}/mcp_debug.rs`):
  `mcp_debug_session` (per-player opt-in flag, keyed on `spacetime_id` per
  HARD-RULES — never a Mojang UUID), `mcp_client_tick` (latest self-reported
  client ground truth), `movement_authority_debug_report` (append log of
  flagged divergences, 10-minute pruned retention like `movement_anomaly`).
- **Zero Stomzy code changes required.** The comparison hooks directly into
  `player_physics_state_update` — the reducer Stomzy already calls ~20 Hz/player
  — so a debug session's ground truth is diffed against server authority the
  instant both sides have reported, with no new Java on the backend.
  mcpclient talks to SpacetimeDB directly over the same plain HTTP wire
  protocol Stomzy's own `SpacetimeBootstrap`/`MaincloudClient` already use
  (`POST /v1/database/{db}/sql` text/plain, `POST /v1/database/{db}/call/{reducer}`
  JSON array body) — no SDK dependency on the client.
- mcpclient side (`dev.terrorify.tools.mcpclient.spacetime`): `SpacetimeConfig`
  (opt-in via `-Dmcpclient.spacetime.enabled=true`, configurable URI/db names —
  no single fixed deployment target exists, mirrors Stomzy's own env-var
  configurability), `SpacetimeClient` (HTTP wire client), `DebugSessionReporter`
  (resolves this player's `spacetime_id` once per connect via a SQL lookup on
  `player_identity` keyed by UUID-as-u128, registers/deregisters the session on
  `ClientPlayConnectionEvents.JOIN`/`DISCONNECT`, streams ground truth every 10
  ticks on `END_CLIENT_TICK`). All calls are async/fire-and-forget — a slow or
  unreachable SpacetimeDB host never blocks the client thread.
- Reports are directly queryable via the existing `spacetimedb` MCP tool with
  zero new tooling: `SELECT * FROM movement_authority_debug_report WHERE
  spacetime_id = ... ORDER BY timestamp DESC` — "a report Claude MCP can
  understand" falls out of using SpacetimeDB as the transport, not a
  separate deliverable.
- **Verified fully live**, not just compiled: published the real `hot` module
  (`cargo check -p hot` clean, then `spacetime publish` — matches the actual
  `spacetimedb = "=2.8.1"` pinned in `spacetime/hot/Cargo.toml`, not the
  stale 2.5.0 in generic docs) to a local SpacetimeDB instance and drove it
  with the exact wire calls mcpclient makes: session enable → client tick
  report → two `player_physics_state_update` calls (server position
  diverging 5.5 blocks from the client's) correctly produced a
  `movement_authority_debug_report` row with the right `pos_delta`/
  `vel_delta`/`note`; disabling the session correctly suppressed further
  reports even on a 45-block divergence; the scheduled prune reducer
  correctly deleted a stale test row on its own. Separately, launched the
  actual mcpclient JVM with the bridge enabled and confirmed via the
  SpacetimeDB server's own request log that `DebugSessionReporter.onJoin()`
  really fires end-to-end (UUID → u128 → SQL query reached the server;
  failed gracefully only because the local test module has no `player_identity`
  table, which lives in cold, not hot).

**Deferred, needs the real backend** (not reachable from this sandbox):

- `movement_stair_plaza.py`'s `STEPS`/waypoints are generic, not the real
  stair-plaza location — swap in real coordinates once walked once.
- The full happy path (a real `player_identity` row resolving a real
  `spacetime_id`, then live comparisons against a real Stomzy backend) needs
  the actual cold+hot deployment with a registered account — every piece up
  to that resolution is proven live in isolation (see above), but the two
  halves haven't been observed joined end-to-end against production/staging.
- Client-side JSONL capture (`movement_diff.py`) still only has the client's
  own belief to compare against for *offline* analysis — the new SpacetimeDB
  path is the live, in-game equivalent and doesn't need a server JSONL sink.
- M2 regression-gate wiring, M3's RouteProbe/RttMeter/ChunkStreamMeter/
  TransferTracer, M4 perception (EntityLedger/HealthProbe/two-client harness),
  M5 optimization loop — all require the live internode/stomzy/SpacetimeDB
  stack, which needs credentials/deployment this session doesn't have.
