from __future__ import annotations

import shutil
import subprocess
from pathlib import Path


def test_java_and_python_demos_share_expected_sorted_output(tmp_path: Path) -> None:
    if shutil.which("javac") is None or shutil.which("java") is None:
        return

    root = Path(__file__).resolve().parents[2]
    build_dir = tmp_path / "java"
    sources = [str(path) for path in (root / "src" / "java").rglob("*.java")]
    subprocess.run(["javac", "-d", str(build_dir), *sources], check=True, cwd=root)
    quick_sort = subprocess.run(
        ["java", "-cp", str(build_dir), "com.sortingsearching.sorting.QuickSort"],
        check=True,
        cwd=root,
        text=True,
        capture_output=True,
    )
    binary_search = subprocess.run(
        ["java", "-cp", str(build_dir), "com.sortingsearching.searching.BinarySearch"],
        check=True,
        cwd=root,
        text=True,
        capture_output=True,
    )

    assert "[1, 3, 7, 7, 19, 42, 99]" in quick_sort.stdout
    assert "Binary Search" in binary_search.stdout
    assert "index : 4" in binary_search.stdout
