# Supported-build packaging

The normal GitHub release and Homebrew distribution remain unchanged. `scripts/package_supported_build.py` adds a separate staging step for a supported-download offering; it does not create tags, publish assets, alter Homebrew, add DRM, or add licence keys.

The staging command requires the three existing release ZIPs for macOS Apple Silicon, Linux x86_64, and Windows x86_64. It fails closed if any platform bundle is missing, copies those exact ZIPs into `dist/supported-build/`, and adds:

- `SHA256SUMS` for customer-side integrity verification;
- `support-manifest.json` with version, source commit, sizes, checksums, and support scope;
- `README-SUPPORTED-BUILD.txt` with concise installation/runtime constraints.

Example for the current version:

```bash
python3 scripts/package_supported_build.py --version v1.1.3
```

The output directory is under `dist/` and remains generated/ignored. A paid storefront should receive the generated platform ZIPs and metadata from this directory rather than a second source fork. The underlying project licence remains unchanged; payment is for the convenient tested distribution and support layer, not for restricting the upstream source.

Before offering a supported build, run the repository's normal build, logic-test, and appropriate smoke/release checks for the release commit. MP4 export still requires `ffmpeg` on `PATH`, and native video playback still depends on the bundled platform-specific Processing/GStreamer runtime.


## Redistribution hold

The current v1.1.3 platform archives are **not cleared for paid redistribution**. The staging script is restricted to internal provenance/checksum use via `--internal-provenance-only`. See `docs/REDISTRIBUTION-AUDIT.md`.
