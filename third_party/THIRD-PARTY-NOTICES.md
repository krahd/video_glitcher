# Third-party notices for video_glitcher supported builds

This file records the principal third-party components intentionally redistributed in the supported-build package. It does **not** change the licence of video_glitcher itself. Project-level licensing is a separate decision and must be explicit before any paid redistribution.

The supported build republishes the exact application release archives rather than rebuilding these dependencies. Binary fingerprints and source provenance are recorded in `source-manifest.json`; licence texts are in `licenses/`; source-access and relinking information is in `SOURCE-ACCESS.md`.

| Component | Observed/bundled version | Licence used for redistribution | Notes |
| --- | --- | --- | --- |
| Processing core | exact bundled `core.jar` fingerprint in manifest | LGPL-2.1 | Processing's core library is LGPL-2.1. |
| ControlP5 | 2.2.6 | LGPL-2.1-or-later | Version embedded in the bundled class. |
| Processing Video | bundled library with GStreamer 1.20.3 runtime | LGPL-2.1-or-later | The exact `video.jar` fingerprint is pinned. |
| gst1-java-core | 1.4.0 | LGPL-family upstream terms | Java bindings used by Processing Video. |
| GStreamer runtime | 1.20.3 family | LGPL-2.1-or-later for GStreamer core/modules, with separately licensed dependencies/plugins | The bundle is the upstream Processing Video runtime; see source-access notes. |
| JNA | 5.12.1 | Apache-2.0 (chosen alternative) | JNA is dual LGPL-2.1-or-later OR Apache-2.0; the supported-build notices elect Apache-2.0. |
| JOGL | 2.6.0, commit `f5964604d3e940c07c3e6af884f06b4eceb08dd4` | BSD-3-Clause | JogAmp OpenGL binding. |
| GlueGen runtime | 2.6.0, commit `c06493e448332ace78721f5dfc3718c863783a12` | BSD-3-Clause | JogAmp runtime used by JOGL. |
| FFmpeg libraries | 4.4 | LGPL-2.1-or-later | Cerbero 1.20 recipe explicitly disables nonfree and does not enable GPL for FFmpeg itself. |
| x264 | Cerbero snapshot `20191217-2245` | GPL-2.0-or-later | Distributed as a separate native library in the GStreamer runtime; corresponding source URL and checksum are pinned in the source manifest. |

The GStreamer runtime contains additional codecs, plugins and support libraries. Those remain under their respective upstream terms. The source manifest points to the exact GStreamer/Cerbero release family used by the bundle and this notice intentionally avoids claiming that every file in that runtime is LGPL.

No warranty is made by the upstream projects. See the included licence texts for the controlling terms.
