#!/usr/bin/env python3
"""Static safety guard for ClearUp's narrowly scoped AccessibilityService."""

from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"

MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
CONFIG = ROOT / "app/src/main/res/xml/accessibility_service_config.xml"
SERVICE = ROOT / (
    "app/src/main/kotlin/com/norvexa/clearup/data/accessibility/"
    "ClearUpAccessibilityService.kt"
)
POLICY = ROOT / (
    "app/src/main/kotlin/com/norvexa/clearup/data/accessibility/"
    "AccessibilityCachePolicy.kt"
)


def fail(message: str) -> None:
    print(f"Accessibility safety check failed: {message}", file=sys.stderr)
    raise SystemExit(1)


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def verify_manifest() -> None:
    tree = ET.parse(MANIFEST)
    root = tree.getroot()
    service = None
    for candidate in root.findall("./application/service"):
        if candidate.get(f"{ANDROID_NS}name") == (
            ".data.accessibility.ClearUpAccessibilityService"
        ):
            service = candidate
            break

    require(service is not None, "AccessibilityService is missing from the manifest")
    require(
        service.get(f"{ANDROID_NS}permission")
        == "android.permission.BIND_ACCESSIBILITY_SERVICE",
        "service must require BIND_ACCESSIBILITY_SERVICE",
    )

    metadata = service.find("meta-data")
    require(metadata is not None, "service metadata is missing")
    require(
        metadata.get(f"{ANDROID_NS}resource")
        == "@xml/accessibility_service_config",
        "service must use the reviewed accessibility config",
    )


def verify_config() -> None:
    root = ET.parse(CONFIG).getroot()
    require(
        root.get(f"{ANDROID_NS}canPerformGestures") == "false",
        "gesture capability must stay disabled",
    )
    require(
        root.get(f"{ANDROID_NS}canRetrieveWindowContent") == "true",
        "window retrieval is required for the reviewed exact-label workflow",
    )
    require(
        root.get(f"{ANDROID_NS}isAccessibilityTool") != "true",
        "ClearUp must not declare itself as a general accessibility tool",
    )
    packages = root.get(f"{ANDROID_NS}packageNames", "")
    require(packages.strip() != "", "settings package allowlist must not be empty")
    require(
        "com.android.settings" in {item.strip() for item in packages.split(",")},
        "AOSP Settings must remain in the package allowlist",
    )


def verify_source() -> None:
    service_text = SERVICE.read_text(encoding="utf-8")
    policy_text = POLICY.read_text(encoding="utf-8")

    forbidden_tokens = (
        "dispatchGesture(",
        "performGlobalAction(",
        "ACTION_SET_TEXT",
        "GLOBAL_ACTION_",
    )
    for token in forbidden_tokens:
        require(token not in service_text, f"forbidden API token found: {token}")

    require(
        "isExactClearCacheLabel" in service_text,
        "service must use exact clear-cache label matching",
    )
    require(
        "rootMatchesTarget" in service_text,
        "service must verify the selected application before clicking",
    )
    require(
        "clear storage" in policy_text.lower()
        and "clear data" in policy_text.lower(),
        "dangerous English labels must remain denied",
    )
    require(
        "стереть данные" in policy_text.lower(),
        "dangerous Russian data-clear label must remain denied",
    )


def main() -> None:
    for path in (MANIFEST, CONFIG, SERVICE, POLICY):
        require(path.is_file(), f"required file is missing: {path.relative_to(ROOT)}")
    verify_manifest()
    verify_config()
    verify_source()
    print("Accessibility safety check passed")


if __name__ == "__main__":
    main()
