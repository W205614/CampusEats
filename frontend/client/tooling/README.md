# Pinned uni-app compatibility layer

The client keeps all DCloud packages at `3.0.0-5020420260813003`. Its old glob and image dependencies had no safe drop-in update. The private package in `fast-glob-compat/` is installed under the dependency key `fast-glob` and explicitly identifies itself as `@campuseats/fast-glob-compat`; it does not claim to be a patched upstream release.

- Scans use tinyglobby and expose the callable, `.async` and `.sync` APIs used by this compiler. Literal directories are not implicitly expanded and directory results retain fast-glob's unmarked form. Unsupported options fail explicitly.
- The copy watcher uses chokidar 4 with relative, bounded patterns. It watches the source tree so new matching directories and files are discovered, then filters file events for the compiler's copy rules. This is a compiler bridge, not the full chokidar 3 API.
- Two guarded patches connect the compiler's copy watcher and Jimp v1 QR reader. `postinstall` checks exact package versions and before/after source hashes, validates the whole set before writing, and permits already-patched bytes.

Run `npm ci --include=optional` and `npm run test:toolchain` from `frontend/client`, followed by the type check and both builds. The same checks run in CI and the Linux Web image. Skipping install scripts leaves the compiler unpatched and compatibility verification fails.

When upgrading DCloud, inspect the actual call sites and upstream changes first. Re-run the scan, watcher and real QR adapter tests; do not merely replace the expected hashes. Never add an audit exception to conceal a compatibility failure.

`fixtures/qr.png` was generated for this repository using qrcode 8.2 and encodes `campuseats-toolchain-compatibility`. It contains no user or service data; the generator is not a project dependency.
