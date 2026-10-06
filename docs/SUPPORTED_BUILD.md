# Internal supported-build inspection

**Not cleared for redistribution or sale.** This tool does not produce a supported product, validate a native runtime, or establish which source built an archive. The normal release and Homebrew workflows remain unchanged. See [redistribution hold](REDISTRIBUTION-AUDIT.md) and [local evaluation](LOCAL-EVALUATION.md).

## Safe internal use

Use Python 3.11+, a POSIX host exposing directory-relative file operations and no-follow flags, and a **complete matching source checkout**, including its tracked dependency files. Internal staging fails closed on Windows or any runtime without those capabilities; Windows archive-inspection and launcher tests are separate from staging support. The three already-existing ZIPs must be in a separate input directory or directly under `dist/`. The output must be a **new immediate child** of this checkout's existing, non-symlink `dist/` directory. Nested output paths and symlink-bearing output parents are unsupported. Only the selected repository root itself may use its trusted lexical/canonical spelling (for example macOS `/var` and `/private/var`); the guard never resolves `dist` or the output leaf to accept an alias. No download, install, archive extraction or executable launch occurs.

```sh
python3 scripts/package_supported_build.py --version v1.1.3 --source dist --output dist/internal-inspection-01 --internal-provenance-only
```

Do not remove an existing directory just to rerun this command. Choose a different output name. Existing files, empty/non-empty directories and symlink destinations are refused. A destination created by another process during preflight is also preserved. Source/dist/output directory handles remain open through inspection and copying. Creation and every write use those handles with no-follow/exclusive flags, so replacing path names cannot redirect writes to a new symlink target. Point-in-time checks refuse replacements they observe; partial data may remain in the original directory if another process moved it. A failed copy may leave partial files for inspection; they are not reused or recursively deleted by the tool.

## Trust and concurrency contract

Use this tool only in a trusted checkout with trusted `dist/` parents. Competing cooperative staging attempts may claim the same new name; exclusive creation ensures only one can claim it. Do not run it while another same-user process is renaming, replacing or substituting the checkout, `dist/` or output directory.

Descriptor-relative no-follow operations prevent writes being redirected by the tested symlink replacements. They are **not a filesystem security sandbox**. Portable mkdir/open cannot atomically prove ownership of a directory against an arbitrary same-user replacement between those operations. Likewise, a rename after the final identity check can move the held output elsewhere or make the reported path stale. The tool does not promise absolute post-rename containment, detection of every replacement, or atomic namespace publication. It never deletes a replacement to recover. Recheck the completed manifest/checksums in the trusted output before using an internal result.

## What is checked

- All three expected filenames must exist as regular ZIP files.
- Each archive must contain exactly the tracked dependency/native files selected by the current release recipe, the matching launcher and the application JAR. Native kinds are explicitly limited to platform `.so`/versioned `.so`, `.dylib` or `.dll` files. Untracked files, personal/sample media, extra runtimes and private configuration are excluded even if accidentally tracked in a native directory. There are currently no native-tree notice files; adding required notices/resources needs a reviewed narrow allowlist exception, never deletion to satisfy the guard. Tracked files are a packaging boundary, not a licence approval.
- Dependency/launcher sizes and SHA-256 hashes must match the selected checkout. JAR flattening collisions, missing files, duplicate/case-colliding paths and implicit ancestor/file-directory collisions (even without ZIP directory entries), traversal, non-canonical paths, symlinks/special entries and oversized archives are refused. Archive contents are never extracted.
- The app JAR is limited to the three application class families and its optional manifest. Class headers report their minimum Java level and reject targets newer than the documented Java 21 build contract. This is structural inspection, not bytecode validation, source attestation or a malware scan.
- Staged bytes are rechecked before completion. Nothing is published.

## Output and truthful provenance

- `SHA256SUMS`: hashes of the exact copied ZIP bytes
- `support-manifest.json`: schema 2, produced last; the valid complete JSON and matching checksums are required for successful internal staging
- `README-SUPPORTED-BUILD.txt`: explicit internal-use warning and runtime/licensing limitations

Schema 2 deliberately replaces the old misleading `sourceCommit` field. `stagingCheckoutCommit` identifies the checkout running this inspection. `archiveSourceCommit` remains `null` and `archiveSourceVerification` is unverified: local HEAD, a requested version label and matching dependency bytes cannot prove the app JAR's source. The input archives may be older than the checkout; a mismatch is a refusal, not permission to relabel them.

To establish build provenance later, generate archive digests from an authorised clean build and retain the exact source SHA, dependency inputs, workflow/run identity and native-platform acceptance evidence. Do not fill these fields by assumption. Signing, notarisation, releases, stores, Homebrew publication and customer support commitments are outside this tool.
