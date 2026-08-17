"""movement_stair_plaza: scripted sprint-jump circuit + capture + movement_diff.

Generic pass/fail movement-authority regression: connect, capture, run a
sprint/jump/turn circuit designed to stress landing/step-up edges, then flag
any server position correction beyond threshold. Exit code 0 = pass.

This is NOT yet wired to the real "stair plaza" test location described in
PLAN.md (specific coordinates on the live map) -- that requires walking the
actual location once to record waypoints. Swap `STEPS` below for a
`run_script` sequence built from real waypoints when available; until then
this exercises the same instrument (capture -> movement_diff) against
whatever server/spawn area `--host` points at.

    python scenarios/movement_stair_plaza.py --host play.example.com --port 25565
    python scenarios/movement_stair_plaza.py --host 127.0.0.1 --port 25566  # local test server
"""
from __future__ import annotations

import argparse
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "reports"))
from mcpc import McpClient  # noqa: E402
from movement_diff import run as movement_diff_run  # noqa: E402

# Sprint forward, jump mid-run (the step-up/landing edge case), turn, repeat.
# Ticks are @ 50ms each (20 TPS).
STEPS = [
    {"ticks": 30, "keys": ["forward", "sprint"]},
    {"ticks": 6, "keys": ["forward", "sprint", "jump"]},
    {"ticks": 20, "keys": ["forward", "sprint"]},
    {"ticks": 10, "keys": ["forward", "sprint"], "yaw": 90.0},
    {"ticks": 6, "keys": ["forward", "sprint", "jump"]},
    {"ticks": 20, "keys": ["forward", "sprint"]},
    {"ticks": 10, "keys": []},  # settle/standstill
]

TICKS_PER_SECOND = 20


def run(host: str, port: int, mcp_host: str, mcp_port: int, threshold: float) -> int:
    mc = McpClient(mcp_host, mcp_port)
    mc.initialize()

    state = mc.get_state()
    if not state["connected"]:
        print(f"connecting to {host}:{port} ...")
        mc.connect(host, port)
        for _ in range(200):  # up to 20s
            time.sleep(0.1)
            if mc.get_state()["connected"]:
                break
        else:
            print("FAIL: never reached connected state")
            return 1
    else:
        print("already connected, reusing session")

    capture = mc.capture_start("movement_stair_plaza")
    capture_dir = capture["dir"]
    print(f"capture: {capture_dir}")

    mc.run_script(STEPS)
    total_ticks = sum(step["ticks"] for step in STEPS)
    time.sleep(total_ticks / TICKS_PER_SECOND + 1.0)

    mc.capture_stop()

    print()
    return movement_diff_run(capture_dir, threshold, context=10)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", required=True, help="Minecraft server to connect to")
    parser.add_argument("--port", type=int, default=25565)
    parser.add_argument("--mcp-host", default="127.0.0.1")
    parser.add_argument("--mcp-port", type=int, default=47800)
    parser.add_argument("--threshold", type=float, default=0.1, help="flag corrections beyond this many blocks")
    args = parser.parse_args()
    raise SystemExit(run(args.host, args.port, args.mcp_host, args.mcp_port, args.threshold))
