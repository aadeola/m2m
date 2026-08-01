"""Fake client service C — calls legacy order endpoints."""

import os
import urllib.request

LEGACY_API_BASE = os.environ.get("LEGACY_API_BASE", "http://localhost:8080")


def fetch(path: str) -> None:
    with urllib.request.urlopen(f"{LEGACY_API_BASE}{path}") as response:
        response.read()


fetch("/orders")
fetch("/orders/5/status")
fetch("/customers/3/orders")
