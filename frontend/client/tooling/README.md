# Pinned uni-app compatibility layer

The client keeps all DCloud packages at `3.0.0-5020420260813003`. Its old glob and image dependencies had no safe drop-in update. The private package in `fast-glob-compat/` is installed under the dependency key `fast-glob` and explicitly identifies itself as `@campuseats/fast-glob-compat`; it does not claim to be a patched upstream release.

- Scans use tinyglobby and expose the callable, `.async` and `.sync` APIs used by this compiler. Literal directories are not implicitly expanded and directory results retain fast-glob's unmarked form. Unsupported options fail explicitly.
- The copy watcher uses chokidar 4 with relative, bounded patterns. It watches the source tree so new matching directories and files are discovered, then filters file events for the compiler's copy rules. This is a compiler bridge, not the full chokidar 3 API.
- Two guarded patches connect the compiler's copy watcher and Jimp v1 QR reader. `postinstall` checks exact package versions and before/after source hashes, validates the whole set before writing, and permits already-patched bytes.

Run `npm ci --include=optional` and `npm run test:toolchain` from `frontend/client`, followed by the type check and both builds. The same checks run in CI and the Linux Web image. Skipping install scripts leaves the compiler unpatched and compatibility verification fails.

When upgrading DCloud, inspect the actual call sites and upstream changes first. Re-run the scan, watcher and real QR adapter tests; do not merely replace the expected hashes. Never add an audit exception to conceal a compatibility failure.

The dependency overrides also pin `body-parser` to `1.20.9` for [GHSA-27r3-jxmh-p828](https://github.com/advisories/GHSA-27r3-jxmh-p828) and `zod` to `4.6.6` for [GHSA-mrhx-c7xc-m3w5](https://github.com/advisories/GHSA-mrhx-c7xc-m3w5). The Zod advisory has no patched 3.x release. DCloud and Jimp remain at their existing versions; compatibility tests exercise both Jimp CommonJS and ESM imports, PNG decoding, resize/crop/cover/contain/rotate/flip, print callbacks and invalid option rejection. These checks cover the retained image-toolchain paths, not every Zod 3 API. No additional source patch or audit exception is used for these two overrides.

Vue I18n and its overridden `core-base`, `message-compiler` and `shared` packages use `11.4.16` for [GHSA-83rm-6c8r-g728](https://github.com/advisories/GHSA-83rm-6c8r-g728) and [GHSA-8rc2-428q-7ww3](https://github.com/advisories/GHSA-8rc2-428q-7ww3); these advisories have no patched 9.x release. `qs` is pinned to `6.16.1` for [GHSA-6pq8-jhm2-2vcw](https://github.com/advisories/GHSA-6pq8-jhm2-2vcw). The DCloud packages and existing compatibility patches remain unchanged. Validate the toolchain, types, H5 and WeChat builds and the affected H5 pages when updating these pins; an audit pass alone does not establish compiler or browser compatibility.

`fixtures/qr.png` was generated for this repository using qrcode 8.2 and encodes `campuseats-toolchain-compatibility`. It contains no user or service data; the generator is not a project dependency.
