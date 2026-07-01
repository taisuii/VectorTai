# Submodule patches

Code-level anti-detection changes that live inside git submodules are kept here
as patches (the submodule pointers stay on upstream). Apply after
`git submodule update --init --recursive`, before building:

```bash
git apply --directory=external/dobby patches/dobby-anon-vma-name.patch
```

- `dobby-anon-vma-name.patch` — names Dobby's executable trampoline arenas
  `dalvik-jit-code-cache` (PR_SET_VMA_ANON_NAME) so they blend into ART's own
  JIT code cache in /proc/pid/maps.
