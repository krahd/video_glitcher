# Local evaluation: first session and remaining delivery gates

Checked 6 October 2026 against draft PR #2, source base `31e439bf616bad22e13242d485c8b3e5e5439840`. This is an internal evaluation checklist, not an installer or a supported-platform claim. The historical v1.1.3 downloads do not include this draft's export/workflow fixes. Do not use them as evidence that the candidate was delivered.

## Before starting

- Use a complete checkout of the candidate on a desktop you control. Keep the terminal open to capture errors. Nothing here needs a sign-in, customer data or an uploaded video.
- Use **JDK 21** to build and **Java 21 with desktop/AWT support** to run. Existing release CI uses JDK 21 without `--release`, so Java 17 compatibility cannot be promised. The release ZIPs include neither Java nor ffmpeg.
- Check `java -version`, `javac -version`, `ffmpeg -version` and `ffmpeg -hide_banner -encoders`. The last output must include `libx264`. Having a program named ffmpeg is insufficient. For recorded inspection, also use `ffprobe`.
- Python 3.11+ is needed for the checks/internal packager. VS Code is optional. The Linux source launcher additionally needs Bash, `ldd` and a compatible native desktop runtime.
- Use an ordinary local filesystem supporting hard links for output. Keep original clips and previous outputs outside the disposable test directory. Do not use a network/removable/cloud-synchronised folder for the first test.
- Stop at any missing native dependency or OS security warning. Do not replace system libraries, disable OS protections or download an unknown executable to make the test continue.

## Platform position

| Target | Evaluation route | Unresolved gate |
| --- | --- | --- |
| Linux x86_64 | Build, then `bash scripts/run-linux.sh` from the checkout. This materialises validated native links in a temporary directory and preflights dependencies. | Stock Debian 13 lacks `libffi.so.7` for this imported runtime. Previous successful native QA used a temporary graphical JRE and official libffi7; those are not shipped dependencies. The legacy portable Linux launcher does not include the source launcher's repair/preflight. |
| macOS Apple Silicon | Build using the README's terminal command, then run with `macos-aarch64` native paths as below. | Clean-machine load/preview/save/cancel and native-runtime provenance are unaccepted. No signing/notarisation is provided. |
| Windows x86_64 | Build in PowerShell with the semicolon-separated classpath below, then run with `windows-amd64` native paths. | Clean-machine load/preview/save/cancel and native-runtime provenance are unaccepted. No installer/signing is provided. |

macOS Intel native files exist in the repository, but the current three release targets include only Apple Silicon macOS. Do not infer Intel Mac, Windows ARM or Linux ARM support from file presence. CI compilation, synthetic ZIP checks and stub-launcher tests do not prove native decoding or GUI acceptance.

macOS source run after compilation, from the checkout root:

```sh
java -cp 'bin:lib/core.jar:lib/controlP5/library/*:lib/processing-opengl/library/*:lib/video/library/*' -Dgstreamer.library.path="$PWD/lib/video/library/macos-aarch64" -Dgstreamer.plugin.path="$PWD/lib/video/library/macos-aarch64/gstreamer-1.0" tom.videoGlitcher.VideoGlitcher
```

Windows PowerShell source build and run, from the checkout root:

```powershell
javac -cp 'lib/core.jar;lib/controlP5/library/*;lib/processing-opengl/library/*;lib/video/library/*' -d bin src/tom/videoGlitcher/VideoGlitcher.java src/tom/videoGlitcher/VideoGlitcherLogic.java src/tom/videoGlitcher/FfmpegVideoExporter.java
java -cp 'bin;lib/core.jar;lib/controlP5/library/*;lib/processing-opengl/library/*;lib/video/library/*' "-Dgstreamer.library.path=$PWD/lib/video/library/windows-amd64" "-Dgstreamer.plugin.path=$PWD/lib/video/library/windows-amd64/gstreamer-1.0" tom.videoGlitcher.VideoGlitcher
```

## First session, using only a synthetic clip

1. Create a fresh local folder for this evaluation. In that folder run this command; `-n` refuses an existing sample filename. It generates colour bars and motion without reading any third-party media or recording a person:

   ```sh
   ffmpeg -n -f lavfi -i testsrc2=size=640x360:rate=24 -t 3 -an -c:v libx264 -pix_fmt yuv420p sample-owned.mp4
   ```

2. Launch the normal app using the appropriate source route above. Press `L`, choose `sample-owned.mp4`, and wait for visible moving colour bars. If first-frame loading fails or times out, retain the terminal error and stop this acceptance run. A blank preview is a failure, even if the window stays open.
3. Click `Subtle`, then `VHS Decay`. Change `Digital Intensity` and `Analogue Intensity` and check that preview changes. Hold `C` to see the source; release to return to effects. Press `?` to inspect the guide and close it again. Press Space to pause and resume; the paused frame must stay coloured.
4. Press `P` for a full clip. Choose a **new** filename `first-result.mp4` in the same disposable folder. Wait for a successful saved-path status. Find the result at the exact destination you selected, not in an assumed application/download folder. Open it in your video player and inspect its first, middle and last moments.
5. If `ffprobe` is available, run:

   ```sh
   ffprobe -v error -show_entries stream=codec_type,codec_name,width,height,r_frame_rate -show_entries format=duration -of json first-result.mp4
   ```

   Expect H.264 video at 24 fps with no audio stream. Dimensions reflect the preview canvas and may include black mattes. Full processing is random real-time capture; exact source duration/frame count and the preview's random sequence are not promised.
6. Press `E`, choose a second new filename `discard-test.mp4`, record briefly, then press `X`. After cancellation, no final file at that name should exist. `E` again or `Stop Output` saves a partial recording; use `X` to discard.
7. Try selecting the existing `first-result.mp4` as a destination. It must be refused and the previous result must remain playable. Cancel a load dialog while paused: the original coloured frame must remain. Then accept the sample again after pause/completed export and check that it starts playing without an extra Play click.

Keep a short result with candidate SHA, OS/CPU, Java/ffmpeg versions, input-generation command, chosen output folder, what passed/failed and relevant error text. Do not mark an unrun platform as passed. Generated videos/screenshots remain local and must not enter the repository or a customer archive.

## Smallest useful next gates

1. Select/rebuild a reproducible native runtime with exact dependency versions, build options, notices and corresponding sources. A checksum alone does not clear its rights. Resolve Linux missing dependencies without a system-library substitution; repeat platform-native QA on clean targets.
2. Establish rights to the project and promotional GIFs and obtain the owner's project-licensing decision. No licence has been chosen on the owner's behalf. The existing [redistribution hold](REDISTRIBUTION-AUDIT.md) remains.
3. Only after runtime and rights are resolved, evaluate a deliberately minimal per-platform payload, install/uninstall experience and unsigned local bundle. Keep signatures, stores and releases as separate authorised work. No new distribution artifact is uploaded by this change.
4. Test whether the preview-to-export workflow saves an editor time. A one-off price remains a hypothesis; tests and packaging do not establish buyer demand or paid-product value.
