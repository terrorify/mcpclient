"""movement_diff: isolates client vs server-corrected position divergence per tick.

Flags any server ClientboundPlayerPositionPacket correction exceeding threshold,
with configurable ±N tick context showing player velocity, ground state, input keys,
outbound move packets, and inbound correction packets.
"""
from __future__ import annotations

import argparse
import math
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence

# Enable importing align from sibling harness directory
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from align import AlignedTick, Capture  # noqa: E402


@dataclass
class CorrectionIncident:
    tick: int
    delta: float
    dx: float
    dy: float
    dz: float
    client_pos: tuple[float, float, float]
    server_pos: tuple[float, float, float]
    teleport_id: int | None
    client_vel: tuple[float, float, float]
    on_ground: bool
    keys: list[str]


def _format_keys(record: dict | None) -> str:
    if not record or "input" not in record:
        return "-"
    inp = record["input"]
    active = []
    if inp.get("fwd"):
        active.append("fwd" if inp["fwd"] > 0 else "back")
    if inp.get("strafe"):
        active.append("right" if inp["strafe"] > 0 else "left")
    if inp.get("jump"):
        active.append("jump")
    if inp.get("sprint"):
        active.append("sprint")
    if inp.get("sneak"):
        active.append("sneak")
    return ",".join(active) if active else "-"


def _format_pos(pos: Sequence[float] | None) -> str:
    if not pos or len(pos) < 3:
        return "       n/a       "
    return f"{pos[0]:8.3f}, {pos[1]:7.3f}, {pos[2]:8.3f}"


def _format_vel(vel: Sequence[float] | None) -> str:
    if not vel or len(vel) < 3:
        return "      n/a      "
    return f"{vel[0]:+6.3f}, {vel[1]:+6.3f}, {vel[2]:+6.3f}"


def analyze_capture(capture: Capture, threshold: float = 0.1) -> list[CorrectionIncident]:
    aligned = capture.aligned()
    incidents: list[CorrectionIncident] = []

    for item in aligned:
        if not item.inbound:
            continue
        rec = item.record
        if not rec or "pos" not in rec:
            continue

        cx, cy, cz = rec["pos"]
        vel = rec.get("vel", (0.0, 0.0, 0.0))
        on_ground = rec.get("on_ground", False)
        keys = rec.get("input", {})

        for in_pkt in item.inbound:
            # Check for position packet (has x, y, z)
            if "x" not in in_pkt or "y" not in in_pkt or "z" not in in_pkt:
                continue

            sx, sy, sz = in_pkt["x"], in_pkt["y"], in_pkt["z"]
            dx = sx - cx
            dy = sy - cy
            dz = sz - cz
            dist = math.sqrt(dx * dx + dy * dy + dz * dz)

            if dist > threshold:
                active_keys = []
                if keys.get("fwd"):
                    active_keys.append("forward")
                if keys.get("sprint"):
                    active_keys.append("sprint")
                if keys.get("jump"):
                    active_keys.append("jump")

                incidents.append(
                    CorrectionIncident(
                        tick=item.tick,
                        delta=dist,
                        dx=dx,
                        dy=dy,
                        dz=dz,
                        client_pos=(cx, cy, cz),
                        server_pos=(sx, sy, sz),
                        teleport_id=in_pkt.get("id"),
                        client_vel=(vel[0], vel[1], vel[2]),
                        on_ground=on_ground,
                        keys=active_keys,
                    )
                )

    return incidents


def print_context(
    aligned_map: dict[int, AlignedTick],
    center_tick: int,
    context: int,
) -> None:
    start_tick = center_tick - context
    end_tick = center_tick + context

    print(f"  {'':2} {'Tick':<8} | {'Client Position':<27} | {'Velocity':<22} | {'Grd':<3} | {'Keys':<14} | Net")
    print(f"  {'-'*2} {'-'*8} | {'-'*27} | {'-'*22} | {'-'*3} | {'-'*14} | {'-'*20}")

    for t in range(start_tick, end_tick + 1):
        item = aligned_map.get(t)
        mark = "*" if t == center_tick else " "
        if not item or not item.record:
            print(f"  {mark} {t:<8} | {'(no record)':<27} | {'':<22} | {'':<3} | {'':<14} |")
            continue

        rec = item.record
        pos_str = _format_pos(rec.get("pos"))
        vel_str = _format_vel(rec.get("vel"))
        grd_str = "T" if rec.get("on_ground") else "F"
        keys_str = _format_keys(rec)

        net_events = []
        if item.outbound:
            net_events.append(f"out:{len(item.outbound)}")
        if item.inbound:
            ids = [str(p.get("id", "?")) for p in item.inbound if "id" in p]
            net_events.append(f"IN:CORR(id={','.join(ids)})" if ids else f"in:{len(item.inbound)}")
        net_str = " ".join(net_events)

        print(f"  {mark} {t:<8} | {pos_str:<27} | {vel_str:<22} | {grd_str:<3} | {keys_str:<14} | {net_str}")


def run(capture_dir: str | Path, threshold: float = 0.1, context: int = 10) -> int:
    cap = Capture.load(capture_dir)
    print("=" * 72)
    print(" Movement Diff Regression Report")
    print("=" * 72)
    print(f"Capture dir: {cap.dir}")
    print(f"Total ticks: {len(cap.ticks)} | Outbound packets: {len(cap.outbound)} | Inbound packets: {len(cap.inbound)}")
    print(f"Flagging threshold: {threshold:.3f} blocks | Context window: ±{context} ticks")
    print()

    incidents = analyze_capture(cap, threshold=threshold)
    if not incidents:
        print(f"PASS: 0 server position corrections > {threshold:.3f} blocks detected.")
        print("=" * 72)
        return 0

    print(f"FAIL: {len(incidents)} server position correction(s) exceeded threshold of {threshold:.3f} blocks:")
    aligned = cap.aligned()
    aligned_map = {item.tick: item for item in aligned}

    for idx, inc in enumerate(incidents, 1):
        print()
        print(f"[{idx}/{len(incidents)}] Correction at Tick {inc.tick}:")
        print(f"  Delta: {inc.delta:.4f} blocks (dx={inc.dx:+.4f}, dy={inc.dy:+.4f}, dz={inc.dz:+.4f})")
        print(f"  Client pos: ({inc.client_pos[0]:.3f}, {inc.client_pos[1]:.3f}, {inc.client_pos[2]:.3f})")
        print(f"  Server pos: ({inc.server_pos[0]:.3f}, {inc.server_pos[1]:.3f}, {inc.server_pos[2]:.3f})  Teleport ID: {inc.teleport_id}")
        print(f"  Client vel: ({inc.client_vel[0]:+.3f}, {inc.client_vel[1]:+.3f}, {inc.client_vel[2]:+.3f})  On ground: {inc.on_ground}")
        print()
        print(f"  Context window (ticks {inc.tick - context} .. {inc.tick + context}):")
        print_context(aligned_map, inc.tick, context)

    print()
    print("=" * 72)
    return 1


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("capture_dir", type=Path, help="Path to capture directory with ticks.jsonl/outbound.jsonl/inbound.jsonl")
    parser.add_argument("--threshold", type=float, default=0.1, help="Flag corrections beyond this distance in blocks (default: 0.1)")
    parser.add_argument("--context", type=int, default=10, help="Number of ticks before/after correction to display (default: 10)")
    args = parser.parse_args()
    raise SystemExit(run(args.capture_dir, threshold=args.threshold, context=args.context))
