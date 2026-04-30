import sys
import os

# Pre-load the stdlib 'queue' module BEFORE adding the gipformer root to sys.path.
# This prevents the local gipformer/queue/ package from shadowing the stdlib module,
# which would break third-party libraries (e.g. hypothesis) that do `from queue import Queue`.
import importlib.util as _ilu
import importlib.machinery as _ilm

_stdlib_queue_path = os.path.join(os.path.dirname(sys.executable), "..", "Lib", "queue.py")
# Use the canonical stdlib path via sys.stdlib_module_names (Python 3.10+)
if hasattr(sys, "stdlib_module_names") and "queue" not in sys.modules:
    # Find the real stdlib queue.py by looking in sys.path entries that don't
    # contain our local package directory
    _gipformer_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    for _path_entry in sys.path:
        if _path_entry == _gipformer_root or _path_entry == "":
            continue
        _candidate = os.path.join(_path_entry, "queue.py")
        if os.path.isfile(_candidate):
            _spec = _ilu.spec_from_file_location("queue", _candidate)
            if _spec is not None:
                _mod = _ilu.module_from_spec(_spec)
                sys.modules["queue"] = _mod
                _spec.loader.exec_module(_mod)
                break

# Ensure the gipformer package root is on sys.path so `from core.vad_chunker import ...` works
_gipformer_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if _gipformer_root not in sys.path:
    sys.path.insert(0, _gipformer_root)
