#!/usr/bin/env python3
"""Extract exported/BROWSABLE activity filters from preserved Android APK trees."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


RESOURCE_APK_PARTS = ("/overlay/", "/framework/", "/etc/")
DENSITY_SUFFIXES = (
    "-hdpi.apk", "-mdpi.apk", "-xhdpi.apk", "-xxhdpi.apk",
    "-xxxhdpi.apk", "-ldpi.apk", "-tvdpi.apk",
)
EXCLUDED_WEB_SCHEMES = {"file", "content", "package", "vnd.android.nfc", "android_secret_code"}


@dataclass
class Node:
    tag: str
    indent: int
    attrs: dict[str, str] = field(default_factory=dict)
    children: list["Node"] = field(default_factory=list)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--aapt2", required=True, type=Path)
    parser.add_argument(
        "--source",
        action="append",
        required=True,
        metavar="LABEL=PATH",
        help="Firmware source label and APK root; may be repeated",
    )
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--workers", type=int, default=8)
    return parser.parse_args()


def parse_sources(values: list[str]) -> list[tuple[str, Path]]:
    sources = []
    for value in values:
        label, separator, raw_path = value.partition("=")
        if not separator or not label or not raw_path:
            raise SystemExit(f"Invalid --source {value!r}; expected LABEL=PATH")
        sources.append((label, Path(raw_path).resolve()))
    return sources


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def attribute_name(body: str) -> str:
    left = body.split("=", 1)[0].strip()
    left = re.sub(r"\(0x[0-9a-fA-F]+\)$", "", left)
    return left.rsplit(":", 1)[-1]


def attribute_value(body: str) -> str:
    quoted = re.search(r'=\"([^\"]*)\"', body)
    if quoted:
        return quoted.group(1)
    plain = re.search(r"=([^\s]+)", body)
    return plain.group(1) if plain else ""


def parse_xmltree(text: str) -> Node:
    root = Node("_root", -1)
    stack = [root]
    for line in text.splitlines():
        stripped = line.lstrip()
        indent = len(line) - len(stripped)
        if stripped.startswith("E: "):
            tag = stripped[3:].split(" ", 1)[0]
            node = Node(tag, indent)
            while stack[-1].indent >= indent:
                stack.pop()
            stack[-1].children.append(node)
            stack.append(node)
        elif stripped.startswith("A: ") and len(stack) > 1:
            body = stripped[3:]
            stack[-1].attrs[attribute_name(body)] = attribute_value(body)
    return root


def child(node: Node, tag: str) -> Node | None:
    return next((item for item in node.children if item.tag == tag), None)


def sample_path(data: list[dict[str, str]]) -> str:
    for key in ("path", "pathPrefix", "pathPattern"):
        for item in data:
            value = item.get(key)
            if not value:
                continue
            if key == "pathPrefix":
                return value + ("probe" if value.endswith("/") else "/probe")
            if key == "pathPattern":
                value = value.replace(".*", "probe")
                value = re.sub(r"(?<!\\)\.", "x", value)
                return value.replace("\\.", ".")
            return value
    return "/"


def sample_uris(data: list[dict[str, str]]) -> list[str]:
    schemes = sorted({item.get("scheme", "") for item in data if item.get("scheme")})
    hosts = sorted({item.get("host", "") for item in data if item.get("host")}) or [""]
    path = sample_path(data)
    values = []
    for scheme in schemes:
        if scheme.startswith("@") or scheme in EXCLUDED_WEB_SCHEMES:
            continue
        for host in hosts:
            clean_host = host.replace("*", "probe")
            if clean_host:
                values.append(f"{scheme}://{clean_host}{path}")
            elif scheme in {"http", "https"}:
                continue
            else:
                values.append(f"{scheme}:")
    return sorted(set(values))


def scan_apk(aapt2: Path, source_label: str, source_root: Path, apk: Path, digest: str) -> dict[str, Any]:
    command = [str(aapt2), "dump", "xmltree", "--file", "AndroidManifest.xml", str(apk)]
    process = subprocess.run(command, capture_output=True, text=True, timeout=45)
    if process.returncode:
        relative_apk = str(apk.relative_to(source_root))
        sanitized_error = process.stderr.strip().replace(str(apk), relative_apk)
        return {
            "error": sanitized_error,
            "source": source_label,
            "apk": relative_apk,
        }

    document = parse_xmltree(process.stdout)
    manifest = child(document, "manifest")
    if manifest is None:
        return {
            "error": "manifest node not found",
            "source": source_label,
            "apk": str(apk.relative_to(source_root)),
        }
    application = child(manifest, "application")
    if application is None:
        return {"filters": []}

    package = manifest.attrs.get("package", "")
    app_permission = application.attrs.get("permission", "")
    filters = []
    for component in application.children:
        if component.tag not in {"activity", "activity-alias"}:
            continue
        for intent_filter in (item for item in component.children if item.tag == "intent-filter"):
            actions = [item.attrs.get("name", "") for item in intent_filter.children if item.tag == "action"]
            categories = [item.attrs.get("name", "") for item in intent_filter.children if item.tag == "category"]
            if "android.intent.category.BROWSABLE" not in categories:
                continue
            data = [dict(item.attrs) for item in intent_filter.children if item.tag == "data"]
            filters.append({
                "package": package,
                "componentType": component.tag,
                "component": component.attrs.get("name", ""),
                "targetActivity": component.attrs.get("targetActivity", ""),
                "exported": component.attrs.get("exported", "implicit"),
                "permission": component.attrs.get("permission", "") or app_permission,
                "actions": actions,
                "categories": categories,
                "data": data,
                "sampleUris": sample_uris(data),
                "evidence": [{
                    "source": source_label,
                    "apk": str(apk.relative_to(source_root)),
                    "sha256": digest,
                }],
            })
    return {"filters": filters}


def filter_key(item: dict[str, Any]) -> str:
    core = {key: value for key, value in item.items() if key != "evidence"}
    return json.dumps(core, sort_keys=True, separators=(",", ":"))


def main() -> None:
    args = parse_args()
    sources = parse_sources(args.source)
    candidates: list[tuple[str, Path, Path, str]] = []
    seen_per_source: set[tuple[str, str]] = set()

    for source_label, source_root in sources:
        for apk in source_root.rglob("*.apk"):
            rendered = str(apk)
            if any(part in rendered for part in RESOURCE_APK_PARTS):
                continue
            if apk.name.endswith(DENSITY_SUFFIXES):
                continue
            try:
                digest = sha256(apk)
            except OSError:
                continue
            identity = (source_label, digest)
            if identity in seen_per_source:
                continue
            seen_per_source.add(identity)
            candidates.append((source_label, source_root, apk, digest))

    filters: dict[str, dict[str, Any]] = {}
    errors = []
    with ThreadPoolExecutor(max_workers=max(1, args.workers)) as executor:
        futures = [
            executor.submit(scan_apk, args.aapt2, label, root, apk, digest)
            for label, root, apk, digest in candidates
        ]
        for future in as_completed(futures):
            result = future.result()
            if "error" in result:
                errors.append(result)
                continue
            for item in result["filters"]:
                key = filter_key(item)
                if key not in filters:
                    filters[key] = item
                else:
                    filters[key]["evidence"].extend(item["evidence"])

    ordered = sorted(filters.values(), key=lambda item: (
        item["package"], item["component"], json.dumps(item["data"], sort_keys=True)
    ))
    cases = []
    for item in ordered:
        actions = item["actions"] or ["android.intent.action.VIEW"]
        uris = item["sampleUris"] or [""]
        for action in actions:
            for uri in uris:
                case_core = f"{item['package']}|{item['component']}|{action}|{uri}"
                cases.append({
                    "id": hashlib.sha256(case_core.encode()).hexdigest()[:16],
                    "label": f"{item['package']} · {item['component'].split('.')[-1]}",
                    "action": action,
                    "uri": uri,
                    "source": {
                        "package": item["package"],
                        "component": item["component"],
                        "exported": item["exported"],
                        "permission": item["permission"],
                        "evidence": item["evidence"],
                    },
                    "manifestBrowserCandidate": item["exported"] == "true" and not item["permission"],
                })

    payload = {
        "schemaVersion": 1,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "sources": [{"label": label} for label, _path in sources],
        "stats": {
            "uniqueApks": len(candidates),
            "browsableFilters": len(ordered),
            "generatedCases": len(cases),
            "errors": len(errors),
        },
        "filters": ordered,
        "cases": cases,
        "errors": errors,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n")


if __name__ == "__main__":
    main()
