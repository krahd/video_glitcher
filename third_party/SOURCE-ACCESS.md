# Source access and replacement information

The supported-build package redistributes third-party libraries separately from the video_glitcher application classes. The Java JARs and platform-native libraries remain discrete files inside each release archive so recipients can inspect and, where technically compatible, replace/relink the libraries independently.

## Exact source pins known from the shipped runtime

- **x264:** GStreamer Cerbero 1.20 uses snapshot `20191217-2245`, source `https://download.videolan.org/pub/x264/snapshots/x264-snapshot-20191217-2245-stable.tar.bz2`, SHA-256 `b2495c8f2930167d470994b1ce02b0f4bfb24b3317ba36ba7f112e9809264160`.
- **FFmpeg:** Cerbero 1.20 uses FFmpeg `4.4`, source `https://ffmpeg.org/releases/ffmpeg-4.4.tar.xz`, SHA-256 `06b10a183ce5371f915c6bb15b7b1fffbe046e8275099c96affc29e17645d909`.
- **JOGL 2.6.0:** implementation commit `f5964604d3e940c07c3e6af884f06b4eceb08dd4` in the JogAmp JOGL repository.
- **GlueGen 2.6.0:** implementation commit `c06493e448332ace78721f5dfc3718c863783a12` in the JogAmp GlueGen repository.
- **JNA 5.12.1:** upstream tag/release 5.12.1; this distribution elects the Apache-2.0 alternative licence.

## Upstream source locations

- Processing core: `https://github.com/processing/processing4`
- ControlP5: `https://github.com/sojamo/controlp5`
- Processing Video: `https://github.com/processing/processing-video`
- gst1-java-core: `https://github.com/gstreamer-java/gst1-java-core`
- GStreamer / Cerbero: `https://gitlab.freedesktop.org/gstreamer/cerbero` (1.20 branch) and `https://gitlab.freedesktop.org/gstreamer/gstreamer`
- JNA: `https://github.com/java-native-access/jna`
- JOGL: `https://jogamp.org/jogl/www/`
- GlueGen: `https://jogamp.org/gluegen/www/`
- FFmpeg: `https://ffmpeg.org/`
- x264: `https://www.videolan.org/developers/x264.html`

## Corresponding-source offer

For any GPL/LGPL component in a paid supported-build distribution for which the exact corresponding source is not shipped in the download itself, the distributor will provide the corresponding machine-readable source, including any distributor-applied changes, for no more than the reasonable cost of transfer. The offer is valid for at least three years after the last supported-build distribution containing that component. Requests must use the support contact published with the paid download/product page.

The release operator must retain the supported-build manifest, the original release archives and this source manifest for the duration of the offer. If the support contact is not yet published, the paid build must not be released.
