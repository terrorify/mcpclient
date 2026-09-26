"""Thin MCP client for mcpclient's HTTP server (stdlib-only, no deps to install).

    from mcpc import McpClient
    mc = McpClient()                 # http://127.0.0.1:47800/mcp
    mc.initialize()
    mc.connect("play.example.com", 25565)
    mc.run_script([{"ticks": 20, "keys": ["forward", "sprint"]}])
    mc.capture_stop()
"""
from __future__ import annotations

import itertools
import json
import urllib.error
import urllib.request
from typing import Any, Iterable, Mapping, Sequence


class McpError(RuntimeError):
    """Raised for a JSON-RPC error or a tool-level isError result."""


class McpClient:
    def __init__(self, host: str = "127.0.0.1", port: int = 47800, timeout: float = 15.0):
        self.url = f"http://{host}:{port}/mcp"
        self.timeout = timeout
        self._ids = itertools.count(1)

    # -- JSON-RPC transport -------------------------------------------------

    def _request(self, method: str, params: Mapping[str, Any] | None = None) -> Any:
        payload = {"jsonrpc": "2.0", "id": next(self._ids), "method": method, "params": params or {}}
        body = json.dumps(payload).encode("utf-8")
        req = urllib.request.Request(
            self.url,
            data=body,
            method="POST",
            headers={"Content-Type": "application/json", "Accept": "application/json"},
        )
        try:
            with urllib.request.urlopen(req, timeout=self.timeout) as resp:
                raw = resp.read().decode("utf-8")
        except urllib.error.HTTPError as e:
            raw = e.read().decode("utf-8")
        envelope = json.loads(raw)
        if "error" in envelope:
            raise McpError(f"{method}: {envelope['error'].get('message')}")
        return envelope.get("result")

    def initialize(self) -> Any:
        return self._request("initialize", {"protocolVersion": "2025-06-18"})

    def list_tools(self) -> list[dict]:
        return self._request("tools/list")["tools"]

    def call_tool(self, name: str, **arguments: Any) -> dict:
        result = self._request("tools/call", {"name": name, "arguments": arguments})
        text = result["content"][0]["text"]
        if result.get("isError"):
            raise McpError(f"{name}: {text}")
        try:
            return json.loads(text)
        except Exception:
            return {"text": text}

    # -- Convenience wrappers over the mcpclient tool surface ---------------

    def get_state(self) -> dict:
        return self.call_tool("get_state")

    def get_ticks(self, from_tick: int | None = None, to_tick: int | None = None) -> dict:
        args: dict[str, Any] = {}
        if from_tick is not None:
            args["from"] = from_tick
        if to_tick is not None:
            args["to"] = to_tick
        return self.call_tool("get_ticks", **args)

    def get_block(self, x: int, y: int, z: int) -> dict:
        return self.call_tool("get_block", x=x, y=y, z=z)

    def get_area(self, min_xyz: Sequence[int], max_xyz: Sequence[int]) -> dict:
        return self.call_tool("get_area", min=list(min_xyz), max=list(max_xyz))

    def connect(self, host: str, port: int = 25565) -> dict:
        return self.call_tool("connect", host=host, port=port)

    def disconnect(self) -> dict:
        return self.call_tool("disconnect")

    def send_chat(self, text: str) -> dict:
        return self.call_tool("send_chat", text=text)

    def run_script(self, steps: Iterable[Mapping[str, Any]]) -> dict:
        return self.call_tool("run_script", steps=list(steps))

    def stop(self) -> dict:
        return self.call_tool("stop")

    def capture_start(self, label: str = "capture") -> dict:
        return self.call_tool("capture_start", label=label)

    def capture_stop(self) -> dict:
        return self.call_tool("capture_stop")
