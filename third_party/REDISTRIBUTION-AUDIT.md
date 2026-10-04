# Redistribution audit for v1.1.3 release archives

Status: **NOT CLEARED FOR PAID REDISTRIBUTION**.

This audit supersedes the earlier assumption that adding a small set of LGPL/GPL/BSD/Apache notice files would make the existing `v1.1.3` release archives suitable for a paid supported-download offering.

## What the archive inspection established

The published platform archives carry a substantially larger multimedia runtime than the initially inventoried Java dependencies. Observed components include:

- FFmpeg-family libraries (`libavcodec`, `libavformat`, `libavutil`, `libswresample`, and related libraries);
- x264 native libraries on macOS and Windows;
- Fraunhofer FDK AAC on Linux (`libfdk_aac.so`) and the corresponding GStreamer plugin (`libgstfdkaac.so`);
- OpenH264 on Windows;
- VisualOn AAC on Windows (`libvo-aacenc-0.dll`);
- a large GStreamer plugin/runtime tree plus numerous codec and support libraries.

The release archives do not currently carry a complete component-by-component licence/notice/source-access package for that runtime. A short principal-components manifest is therefore insufficient evidence of redistribution compliance.

## Why this blocks the paid-binary experiment

1. **FFmpeg configuration matters.** FFmpeg is normally LGPL, but enabling GPL components changes the effective FFmpeg licence; combining incompatible/nonfree components can make an FFmpeg binary unredistributable. The shipped binaries must be traced to their exact build configuration rather than inferred from a generic Cerbero recipe.
2. **x264 carries GPL obligations.** x264 is GPL-licensed. Its presence is not by itself a reason the application cannot exist, but redistribution requires the applicable GPL obligations to be satisfied and the interaction with the exact GStreamer/FFmpeg build to be understood.
3. **FDK AAC is not a routine permissive dependency.** Its upstream licence requires the complete licence text with binary redistribution, requires source availability, and expressly grants no patent licence. Commercial distribution therefore needs a deliberate patent/licensing assessment rather than a generic NOTICE file.
4. **The runtime inventory is much larger than the earlier compliance manifest.** OpenH264, VisualOn AAC, codec libraries and many GStreamer plugins/support libraries are present and need exact provenance and applicable terms.
5. **video_glitcher itself still has no explicit project-level licence.** That remains a separate precondition before any new paid redistribution path is launched.

## Decision

Do **not** upload or sell the current `v1.1.3` macOS/Linux/Windows archives as a paid supported-build product.

The existing packaging script remains useful only for internal provenance/checksum staging. Its output must not be described as redistribution-ready.

Reopen a paid-binary experiment only if one of these bounded paths becomes worthwhile:

- produce a clean, reproducible runtime with an intentionally selected redistributable codec set and a complete dependency/SBOM/licence/source package; or
- obtain authoritative provenance and redistribution/patent clearance for the exact existing runtime.

Until then, further storefront polish, entitlement work, signing/notarisation work for this paid-binary hypothesis, and itch.io upload work are sunk-cost expansion and should not proceed.

## Authoritative upstream references used for this stop decision

- FFmpeg licence: https://ffmpeg.org/doxygen/trunk/md_LICENSE.html
- GStreamer licensing FAQ: https://gstreamer.freedesktop.org/documentation/frequently-asked-questions/licensing.html
- GStreamer plugin split/distributor guidance: https://gstreamer.freedesktop.org/documentation/additional/splitup.html
- Fraunhofer FDK AAC licence text (upstream source header): https://github.com/mstorsjo/fdk-aac/blob/master/libAACenc/include/aacenc_lib.h

This document records an engineering/commercial risk decision, not legal advice.
