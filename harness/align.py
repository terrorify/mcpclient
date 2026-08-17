"""Loads a mcpclient capture directory (ticks.jsonl / outbound.jsonl / inbound.jsonl)
into tick-indexed structures. All three streams share the same tick counter and were
sampled/flushed from the client thread in a fixed order (see CaptureWriter.java), so
alignment here is a plain group-by-tick join -- no clock-offset estimation needed for
single-client captures.
"""
from __future__ import annotations

import json
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any


def _read_jsonl(path: Path) -> list[dict]:
    if not path.exists():
        return []
    records = []
    with path.open("r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                records.append(json.loads(line))
    return records


@dataclass
class AlignedTick:
    tick: int
    record: dict | None = None
    outbound: list[dict] = field(default_factory=list)
    inbound: list[dict] = field(default_factory=list)


@dataclass
class Capture:
    dir: Path
    ticks: list[dict]
    outbound: list[dict]
    inbound: list[dict]

    @classmethod
    def load(cls, capture_dir: str | Path) -> "Capture":
        d = Path(capture_dir)
        return cls(
            dir=d,
            ticks=_read_jsonl(d / "ticks.jsonl"),
            outbound=_read_jsonl(d / "outbound.jsonl"),
            inbound=_read_jsonl(d / "inbound.jsonl"),
        )

    def aligned(self) -> list[AlignedTick]:
        by_tick: dict[int, AlignedTick] = {}

        def get(tick: int) -> AlignedTick:
            if tick not in by_tick:
                by_tick[tick] = AlignedTick(tick=tick)
            return by_tick[tick]

        for r in self.ticks:
            get(r["tick"]).record = r
        for r in self.outbound:
            get(r["tick"]).outbound.append(r)
        for r in self.inbound:
            get(r["tick"]).inbound.append(r)

        return [by_tick[t] for t in sorted(by_tick)]

    def tick_range(self) -> tuple[int, int] | None:
        ticks = [r["tick"] for r in self.ticks]
        if not ticks:
            return None
        return min(ticks), max(ticks)


if __name__ == "__main__":
    import sys

    if len(sys.argv) != 2:
        print("usage: align.py <capture_dir>")
        raise SystemExit(1)

    cap = Capture.load(sys.argv[1])
    print(f"ticks={len(cap.ticks)} outbound={len(cap.outbound)} inbound={len(cap.inbound)}")
    rng = cap.tick_range()
    if rng:
        print(f"tick range: {rng[0]}..{rng[1]}")
