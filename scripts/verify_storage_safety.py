#!/usr/bin/env python3
"""Static safety guard for directory cleanup and local report sharing."""

from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"

MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
FILE_PATHS = ROOT / "app/src/main/res/xml/file_paths.xml"
REPOSITORY = ROOT / (
    "app/src/main/kotlin/com/norvexa/clearup/data/directories/"
    "EmptyDirectoryRepository.kt"
)
POLICY = ROOT / (
    "app/src/main/kotlin/com/norvexa/clearup/data/directories/"
    "EmptyDirectoryPolicy.kt"
)
EXPORTER = ROOT / (
    "app/src/main/kotlin/com/norvexa/clearup/data/report/ReportExporter.kt"
)
VIEW_MODEL = ROOT / (
    "app/src/main/kotlin/com/norvexa/clearup/feature/directories/"
    "EmptyDirectoriesViewModel.kt"
)


def fail(message: str) -> None:
    print(f"Storage safety check failed: {message}", file=sys.stderr)
    raise SystemExit(1)


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def verify_manifest() -> None:
    root = ET.parse(MANIFEST).getroot()
    permissions = {
        item.get(f"{ANDROID_NS}name"): item
        for item in root.findall("uses-permission")
    }
    write_permission = permissions.get("android.permission.WRITE_EXTERNAL_STORAGE")
    require(write_permission is not None, "legacy write permission is missing")
    require(
        write_permission.get(f"{ANDROID_NS}maxSdkVersion") == "29",
        "legacy write permission must stop at Android 10",
    )

    provider = None
    for candidate in root.findall("./application/provider"):
        if candidate.get(f"{ANDROID_NS}name") == "androidx.core.content.FileProvider":
            provider = candidate
            break
    require(provider is not None, "FileProvider is missing")
    require(provider.get(f"{ANDROID_NS}exported") == "false", "FileProvider must not be exported")
    require(
        provider.get(f"{ANDROID_NS}grantUriPermissions") == "true",
        "FileProvider must use temporary URI grants",
    )


def verify_file_paths() -> None:
    root = ET.parse(FILE_PATHS).getroot()
    tags = [child.tag for child in root]
    require("root-path" not in tags, "root-path must never be exposed")
    require("external-path" not in tags, "external-path must never be exposed")

    reports = [
        child
        for child in root.findall("cache-path")
        if child.get(f"{ANDROID_NS}name") == "reports"
    ]
    require(len(reports) == 1, "reports must have exactly one cache-path")
    require(
        reports[0].get(f"{ANDROID_NS}path") == "reports/",
        "reports cache-path must stay restricted to reports/",
    )


def verify_directory_source() -> None:
    repository = REPOSITORY.read_text(encoding="utf-8")
    policy = POLICY.read_text(encoding="utf-8")
    view_model = VIEW_MODEL.read_text(encoding="utf-8")

    for token in ("deleteRecursively", "walkTopDown", "rm -rf", "Runtime.getRuntime"):
        require(token not in repository, f"forbidden recursive deletion token found: {token}")

    for token in (
        "isSymbolicLink",
        "children.isNotEmpty()",
        "isAllowedCandidate",
        "canonical != candidate.canonicalPath",
        "MAX_VISITED_DIRECTORIES",
    ):
        require(token in repository, f"required deletion safety check is missing: {token}")

    require('ANDROID_DIRECTORY = "Android"' in policy, "Android/ exclusion is missing")
    require("selectedPaths = emptySet()" in view_model, "scan results must not be preselected")
    require("repository.scan(ageDays)" in view_model, "post-deletion rescan is required")


def verify_exporter() -> None:
    source = EXPORTER.read_text(encoding="utf-8")
    require("FileProvider.getUriForFile" in source, "report must use FileProvider")
    require("FLAG_GRANT_READ_URI_PERMISSION" in source, "temporary read grant is missing")
    require("appContext.cacheDir" in source, "reports must be created in app cache")
    require("Uri.fromFile" not in source, "file:// report sharing is forbidden")
    require("MAX_RETAINED_REPORTS = 10" in source, "cached report retention limit is missing")


def main() -> None:
    for path in (MANIFEST, FILE_PATHS, REPOSITORY, POLICY, EXPORTER, VIEW_MODEL):
        require(path.is_file(), f"required file is missing: {path.relative_to(ROOT)}")
    verify_manifest()
    verify_file_paths()
    verify_directory_source()
    verify_exporter()
    print("Storage safety check passed")


if __name__ == "__main__":
    main()
