![ARCHON — crowned chess king and arch emblem](assets/archon-banner.png)

# Archon — Archchess chess engine plugin

Archon is a pure-Java CPU chess engine for Archchess. The project lives at
`C:\Users\reiva\Desktop\Javier\dev\Java\archon-engine`; the built, self-contained jar is
`target\archon.jar`. Requires Java 25. No external engine to install or link.

The original identity artwork is in `assets/archon-logo.png` (square icon) and
`assets/archon-banner.png` (banner). The icon is also bundled in the jar as
`archon/archon-logo.png` for future Archchess display support.
The built-in image generation prompts are recorded in
[`assets/generation-prompts.txt`](assets/generation-prompts.txt).

## Install

1. Build with `mvn -q package`, or download `archon.jar` from a release.
2. Copy `target\archon.jar` into Archchess' plugins folder: `plugins.path` in
   `archchess.properties`. The default is `temp/plugins` inside the `chess-software` project,
   locally `C:\Users\reiva\Desktop\Javier\dev\Java\chess-software\temp\plugins`.
3. Select **Archon** as the CPU engine. Archchess discovers the service with `ServiceLoader`
   and reloads its plugins every minute. Repeated `start()` calls are harmless and preserve configuration.

## Engine

- Twelve piece bitboards, precomputed classical sliding attack rays, all six FEN fields,
  reversible moves, incremental Zobrist hashes, and full legal move generation including
  castling, en passant, and all four promotions.
- Iterative deepening, principal variation search, aspiration windows, mate distance scores,
  and a fixed 262,144-entry transposition table with depth-preferred replacement and aging.
- TT moves, MVV-LVA captures, static exchange evaluation (SEE), killers, and history ordering;
  quiescence with SEE pruning, null-move pruning, late move reductions, and check extensions.
- Tapered material and generated piece-square tables, mobility, doubled/isolated/passed pawns,
  bishop pair, open-file rooks, pawn shields, and king-ring pressure.
- Threefold repetition, the 50-move rule, stalemate, and basic insufficient-material detection.
  Checkmate takes precedence over a draw counter. Repetitions are tracked inside the search and
  across sequential requests when the intervening opponent move can be inferred. A single FEN
  does not contain earlier game history; switching games or loading an unrelated FEN resets that history.
- One search thread, synchronized plugin methods, no subprocesses or native libraries.

Archon uses traditional handcrafted evaluation; it is an independent engine, with no claim of
Stockfish-level playing strength.

## Difficulty and timing

| Difficulty | Depth cap | Move selection |
| --- | ---: | --- |
| EASY | 2 | Randomized selection among moves within 180 cp of the best root score |
| MEDIUM | 5 | Small randomized selection within 25 cp |
| HARD | 9 | Best searched move |
| MAX | Time-limited, with a 96-ply stack guard | Best searched move |

Positive `PluginConfiguration.moveTimeMs()` values set the time budget; the default is 1000 ms.
Nonpositive values preserve the current budget. Archchess normally supplies 50 ms.
The engine completes at least depth 1 and always retains a legal fallback, then checks the hard
deadline every 256 search nodes and between root moves. Depth 1, JVM warm-up, scheduling, or
garbage collection can cause a small time overrun. Only completed iterations supply the move.
The public response details contain only `depth`, `nodes`, `nps`, and `time` (milliseconds).
Evaluation and expected move sequences stay inside the engine so the human player cannot read them.

## Build and tests

The only runtime dependency is `archchess-plugins 1.0`, vendored as a Maven repository in `lib/`
and included by Maven Shade. JUnit Jupiter is test-scoped. No engine assets or tables are downloaded.

```powershell
$env:JAVA_HOME = 'C:\Users\reiva\.jdks\openjdk-25.0.1'
& 'C:\Users\reiva\AppData\Local\Programs\IntelliJ IDEA\plugins\maven-plugin\lib\maven3\bin\mvn.cmd' -q package
& "$env:JAVA_HOME\bin\java.exe" -jar target\archon.jar
```

The JUnit suite includes start position through depth 5, canonical Kiwipete through depth 4,
position 3 through depth 5, additional promotion/check benchmarks, FEN/hash undo, legal PVs,
mate in one, both colors' promotions, terminal positions, draw detection, and 50 ms requests.

| Position | Depth | Expected leaf nodes |
| --- | ---: | ---: |
| Start | 1–5 | 20, 400, 8,902, 197,281, 4,865,609 |
| Kiwipete | 1–4 | 48, 2,039, 97,862, 4,085,603 |
| Position 3 | 1–5 | 14, 191, 2,812, 43,238, 674,624 |

Canonical benchmark FENs:

```text
Kiwipete:  r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1
Position 3: 8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1
```

## Protocol

Run `java -jar target/archon.jar`. Each request is one stdin line; stdout replies start with `$archchess:`.

| Input | Reply |
| --- | --- |
| `name` | `$archchess:Archon` |
| `move <FEN>` | `$archchess:{"from":"e2","to":"e4","details":{"depth":"7","nodes":"40000","nps":"800000","time":"50"}}` |
| `config <EASY\|MEDIUM\|HARD\|MAX> [moveTimeMs]` | `$archchess:{"ok":"true"}` |
| `perft <depth> [FEN]` | `$archchess:{"depth":"5","nodes":"4865609","time":"...","nps":"..."}` |
| `exit` | Exits without a reply |

Promotions add `"promote_to":"queen"`, `"rook"`, `"bishop"`, or `"knight"`.
Invalid input and positions with no legal moves return `{"error":"..."}` after the same prefix.
`perft` defaults to the starting position and accepts depths 0–32 (large depths can take a long time).
Perft times include move generation and make/unmake, and NPS counts leaf nodes.

## Releases

Push a `v*` tag to run `.github/workflows/release.yml`: Java 25 builds and tests the shaded jar,
then attaches `target/archon.jar` to a GitHub Release. The workflow does not fetch an external engine.
