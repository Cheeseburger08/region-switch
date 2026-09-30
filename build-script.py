"""Compile the per-app hooks. No device connection or root action is performed."""
from pathlib import Path
import frida

HERE = Path(__file__).resolve().parent
if frida.__version__ != "17.9.11":
    raise SystemExit("Install the pinned requirements-build.txt first.")
source = frida.Compiler().build("hooks/region-switch.js", project_root=str(HERE))
(HERE / "region.js").write_bytes(source.encode("utf-8"))
print("Built region.js")
