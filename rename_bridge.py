#!/usr/bin/env python3
# Align VectorTai legacy API to the LSPlantTai (com.android.bridge.Ts*) naming
# so modules migrate 1:1. Runs from repo root. Legacy API lives in legacy/ (tracked),
# consumers in daemon/zygisk/app/services; native uses HookBridge (unaffected).
import os, re, shutil

ROOT = os.path.dirname(os.path.abspath(__file__))
os.chdir(ROOT)

INCLUDE = ["legacy", "daemon", "zygisk", "app",
           "services/daemon-service", "services/manager-service", "native"]
EXTS = (".java", ".kt", ".cpp", ".h", ".hpp", ".aidl", ".xml", ".pro", ".kts")
SKIP = ("/build/", "/.git/", "/.gradle/", "/external/",
        "/xposed/libxposed/", "/services/libxposed/")

# order: most specific first (word-boundary regex, so mostly order-independent)
CLASS = [
    ("IXposedHookInitPackageResources", "IInitPackageResourcesHook"),
    ("IXposedHookLoadPackage", "ILoadPackageHook"),
    ("IXposedHookZygoteInit", "IZygoteInitHook"),
    ("IXposedHookCmdInit", "ICmdInitHook"),
    ("XC_InitPackageResources", "TsInitPackageResources"),
    ("XC_MethodReplacement", "TsMethodReplacement"),
    ("XC_LayoutInflated", "TsLayoutInflated"),
    ("XC_MethodHook", "TsMethodHook"),
    ("XC_LoadPackage", "TsLoadPackage"),
    ("XSharedPreferences", "TsSharedPreferences"),
    ("XposedHelpers", "TsHelpers"),
    ("XposedBridge", "TsBridge"),
    ("XposedInit", "BridgeInit"),
    ("IXposedMod", "IModuleHook"),
    ("XCallback", "BridgeCallback"),
    ("IXUnhook", "IUnhook"),
]
PKG = [("dev.android.runtime.ext", "com.android.bridge"),
       ("dev/android/runtime/ext", "com/android/bridge")]
CMAP = dict(CLASS)

def transform(text):
    for a, b in CLASS:
        text = re.sub(r"\b" + re.escape(a) + r"\b", b, text)
    for a, b in PKG:
        text = text.replace(a, b)
    return text

def skip(p):
    p = "/" + p.replace("\\", "/") + "/"
    return any(s in p for s in SKIP)

# 1. content replacement
changed = 0
for base in INCLUDE:
    for dp, _, fs in os.walk(base):
        if skip(dp):
            continue
        for f in fs:
            if not f.endswith(EXTS):
                continue
            fp = os.path.join(dp, f)
            try:
                s = open(fp, encoding="utf-8").read()
            except Exception:
                continue
            t = transform(s)
            if t != s:
                open(fp, "w", encoding="utf-8").write(t)
                changed += 1
print("content files changed:", changed)

# 2. move package dir + rename class files (only legacy holds the ext sources)
moved = 0
for base in INCLUDE:
    for dp, _, _ in list(os.walk(base)):
        dpn = dp.replace("\\", "/")
        if dpn.endswith("/dev/android/runtime/ext") and not skip(dp):
            dst = dpn[:-len("dev/android/runtime/ext")] + "com/android/bridge"
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.move(dpn, dst)
            moved += 1
            for d2, _, fs in os.walk(dst):
                for f in fs:
                    if f.endswith(".java"):
                        stem = f[:-5]
                        if stem in CMAP:
                            os.rename(os.path.join(d2, f),
                                      os.path.join(d2, CMAP[stem] + ".java"))
            # prune now-empty dev/ tree
            devbase = dpn[:-len("/android/runtime/ext")]  # .../dev
            for sub in ("android/runtime", "android", ""):
                d = os.path.join(devbase, sub) if sub else devbase
                try:
                    os.rmdir(d)
                except OSError:
                    pass
print("package dirs moved:", moved)
print("DONE")
