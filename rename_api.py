#!/usr/bin/env python3
"""De-fingerprint the Xposed API: rename package namespaces + module-recognition
contract across the VectorTai source tree (excluding build/, .git/, external deps)."""
import os, shutil

ROOT = os.path.dirname(os.path.abspath(__file__))
EXTS = (".java", ".kt", ".cpp", ".h", ".hpp", ".cc", ".aidl",
        ".gradle.kts", ".kts", ".pro", ".xml", ".toml")
# external non-API submodules to skip (they never reference xposed)
SKIP_DIRS = ("/build/", "/.git", "/external/lsplant", "/external/dobby",
             "/external/fmt", "/external/xz-embedded", "/external/lsplt",
             "/external/apache", "/external/axml")

# ordered content replacements (dot + slash forms, then contract keys)
REPL = [
    ("de.robv.android.xposed", "dev.android.runtime.ext"),
    ("de/robv/android/xposed", "dev/android/runtime/ext"),
    ("io.github.libxposed", "dev.android.runtime"),
    ("io/github/libxposed", "dev/android/runtime"),
    ('"xposedminversion"', '"rt.min.version"'),
    ('"xposedscope"', '"rt.scope"'),
    ('"xposedsharedprefs"', '"rt.shared.prefs"'),
    ("xposed_init", "rt_init"),
]
# package-dir renames (path suffix -> new suffix)
DIR_MOVES = [
    ("de/robv/android/xposed", "dev/android/runtime/ext"),
    ("io/github/libxposed", "dev/android/runtime"),
]

def skip(path):
    p = path.replace("\\", "/")
    return any(s in p for s in SKIP_DIRS)

def is_target(fn):
    return fn.endswith(EXTS)

# ---- 1. content replacement ----
changed = []
for dp, dn, fns in os.walk(ROOT):
    if skip(dp):
        dn[:] = [d for d in dn if not skip(os.path.join(dp, d))]
        continue
    for fn in fns:
        if not is_target(fn):
            continue
        fp = os.path.join(dp, fn)
        try:
            with open(fp, "r", encoding="utf-8") as f:
                txt = f.read()
        except (UnicodeDecodeError, IsADirectoryError):
            continue
        new = txt
        for a, b in REPL:
            new = new.replace(a, b)
        if new != txt:
            with open(fp, "w", encoding="utf-8") as f:
                f.write(new)
            changed.append(fp)

print(f"== content: {len(changed)} files modified ==")
for c in sorted(changed):
    print("  ", c.replace(ROOT + "/", ""))

# ---- 2. directory moves (package dirs) ----
moved = []
for old_rel, new_rel in DIR_MOVES:
    hits = []
    for dp, dn, fns in os.walk(ROOT):
        if skip(dp):
            dn[:] = [d for d in dn if not skip(os.path.join(dp, d))]
            continue
        if dp.replace("\\", "/").endswith("/" + old_rel):
            hits.append(dp)
    for d in hits:
        new_d = d[: -len(old_rel)] + new_rel
        os.makedirs(os.path.dirname(new_d), exist_ok=True)
        if os.path.exists(new_d):
            for item in os.listdir(d):
                shutil.move(os.path.join(d, item), os.path.join(new_d, item))
            os.rmdir(d)
        else:
            shutil.move(d, new_d)
        moved.append((d, new_d))

print(f"== dirs moved: {len(moved)} ==")
for a, b in moved:
    print("  ", a.replace(ROOT + "/", ""), "->", b.replace(ROOT + "/", ""))

# ---- 3. residual sanity (should be zero outside build/external) ----
print("== residual old tokens (should be none) ==")
import subprocess
res = subprocess.run(
    ["grep", "-rlE", "de\\.robv\\.android\\.xposed|io\\.github\\.libxposed|de/robv/android/xposed|io/github/libxposed",
     ROOT], capture_output=True, text=True)
leftovers = [l for l in res.stdout.splitlines()
             if not skip(l) and l.endswith(EXTS)]
print("  leftover files:", len(leftovers))
for l in leftovers[:20]:
    print("   ", l.replace(ROOT + "/", ""))
print("DONE")
