Status: **NOT CLEARED FOR PAID REDISTRIBUTION**.

The v1.1.3 release archives include a substantially larger multimedia runtime than the earlier compliance manifest covered, including FFmpeg-family libraries, x264, FDK AAC on Linux, OpenH264 and VisualOn AAC on Windows, plus a large GStreamer plugin/runtime tree. The earlier principal-components notice package is insufficient evidence of redistribution compliance and is superseded by this hold.

Do not upload or sell the current v1.1.3 macOS/Linux/Windows archives as a paid supported-build product.

Reopen paid binary redistribution only after either: (1) producing a clean reproducible runtime with an intentionally selected redistributable codec set and complete SBOM/licence/source package, or (2) obtaining authoritative provenance and redistribution/patent clearance for the exact existing runtime. Project-level licensing for video_glitcher also remains a separate prerequisite.

Until then, storefront polish, entitlement work, signing/notarisation work for this paid-binary hypothesis, and itch.io upload work must not proceed.

This is an engineering/commercial risk decision, not legal advice.

## Source/runtime inventory checked 6 October 2026

Repository base: `31e439bf616bad22e13242d485c8b3e5e5439840`. Inspection of its complete Git tree and packaging recipes found no separately tracked project `LICENSE`, `COPYING` or `NOTICE` files. This does not establish whether a JAR embeds notices, nor does a missing project licence prove the owner lacks rights. Exact binary-to-source provenance and the owner's intended project licence remain unestablished.

The release recipe copies 13 dependency JARs, the application JAR, one launcher and the chosen native tree. All OpenGL native JAR variants are copied to every platform by the existing recipe. `lib/VideoExport/` is present in the repository (properties identify 0.2.3) but is not included by that recipe or the maintained Java application classpath. Do not silently expand the shipped surface to include it.

Native file inventory, uncompressed Git blob sizes (not download sizes):

| Tree | Tracked files | Bytes | Examples requiring exact provenance |
| --- | ---: | ---: | --- |
| `macos-aarch64` | 225 | 108,620,000 | FFmpeg `libavcodec.58*`, `libavformat.58*`, `libx264.157.dylib`, OpenH264 |
| `macos-x86_64` (not a release target) | 226 | 119,448,128 | Same codec families; presence does not establish supported Intel-Mac delivery |
| `linux-amd64` | 424 | 376,335,017 | FFmpeg libraries, FDK AAC plugin/library and OpenH264; 155 small files are imported link placeholders |
| `windows-amd64` | 305 | 297,163,110 | `avcodec-58.dll`, `avformat-58.dll`, `libx264-157.dll`, OpenH264 and VisualOn AAC |

These filenames identify audit leads, not the exact configuration of a linked binary or its redistribution terms. In particular, FDK and GPL-family filenames coexisting in a folder do not alone prove an FFmpeg binary was built with `--enable-nonfree`.

## Official upstream findings and missing evidence

Sources below were read on 6 October 2026. Upstream licence information is context for the audit; it is not proof that the vendored bytes match that upstream version or fulfil all obligations.

- Processing core: upstream identifies core as LGPL 2.1; other Processing components have different terms. The exact `core.jar` version/build and corresponding sources need matching. [Processing licence](https://github.com/processing/processing4/blob/main/LICENSE.md)
- Processing video: repository properties identify 2.2.2. Upstream `Movie.java` states LGPL 2.1 or later; its native runtime is a separate dependency surface. [Official source header](https://github.com/processing/processing-video/blob/main/src/processing/video/Movie.java)
- ControlP5: repository properties identify 2.2.6. Upstream states LGPL 2.1 or later. Match the actual JAR and preserve notices/source obligations. [ControlP5 licence](https://github.com/sojamo/controlp5/blob/master/LICENSE.md)
- JOGL/GlueGen: filenames identify 2.6.0. Their upstream licences include multiple notices and third-party components; reducing them to a single BSD label is inadequate. [JOGL licence](https://raw.githubusercontent.com/sgothel/jogl/master/LICENSE.txt), [GlueGen licence](https://raw.githubusercontent.com/sgothel/gluegen/master/LICENSE.txt)
- gst1-java-core: filename identifies 1.4.0. Upstream licence is LGPL 3; obtain the corresponding release source/notices. [Project and licence](https://github.com/gstreamer-java/gst1-java-core)
- JNA: the shipped `lib/video/library/jna.jar` has no version in its filename. Upstream permits LGPL 2.1-or-later or, from version 4.0, Apache 2.0. Verify the version before recording the applicable option. [JNA licence](https://github.com/java-native-access/jna/blob/master/LICENSE)
- GStreamer: core LGPL licensing does not clear every plugin, linked codec or patent concern. Audit the selected plugin set and transitive native dependencies. [GStreamer licensing advisory](https://gstreamer.freedesktop.org/documentation/application-development/appendix/licensing.html)
- FFmpeg: upstream documents LGPL/GPL configuration changes, matching source/build-information requirements, and incompatible combinations enabled using `--enable-nonfree`. x264 affects GPL status; FDK compatibility depends on the combination. Resolve actual build configurations rather than labelling every FFmpeg binary alike. Patent questions remain separate and jurisdiction-dependent. [FFmpeg legal guidance](https://ffmpeg.org/legal.html), [FFmpeg licence/configuration rules](https://github.com/FFmpeg/FFmpeg/blob/master/LICENSE.md)
- Export invokes external system ffmpeg with `libx264`. It is not bundled by this release recipe, but FFmpeg-family shared libraries still are present in the decoder trees. Externalising the export executable does not remove those bundled dependencies from the audit.
- Three promotional GIFs are tracked under `assets/` and `docs/assets/`; the inspected tree contains no accompanying provenance record. Do not infer ownership or reuse rights. They are not copied by the release recipe or allowed by the internal archive inspector. First-session testing uses only a generated pattern clip.

## Decision and evidence required to lift the hold

The owner must confirm authority over the application's source/contributions and intended project licence, and identify rights to promotional media before it is repurposed. No agent should select a new project licence by inference. A separate dependency review must tie every shipped binary to version, supplier/source, build flags, licence/notices, corresponding source or other required materials, and the intended distribution model. A reproducible reduced runtime is preferable to blessing this broad inherited payload by filename. Qualified advice may be needed for codec/patent and distribution obligations.

No rights, notices, vendored binaries, project licence, existing release or store state were changed by this inventory. Internal allowlist/hash checks and a green build are not legal clearance or native acceptance.
