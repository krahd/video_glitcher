# Paused Rewind and incomplete native recording investigation

## Result and limits

On 7 October 2026, normal-GUI observations on source `da18317752f774a5a265100fefcd26c9f67a1b88` confirmed that paused Rewind retains the old displayed frame throughout the measured intervals. Play subsequently starts near zero.

**The subsequent live-recording investigation did not pass.** Its original Java process was killed with exit 137; the log also contained a disposed-native-buffer callback exception. The cause remains unresolved. A single controlled retry was deliberately terminated by a memory-pressure guard before an output destination was accepted. Neither event establishes successful manually paused live recording, ONCE-end recording-gate behaviour, native stability or a resolved failure.

The separate positive [recovery checkpoint](NATIVE-QA-2026-10-07.md) remains a historical observation of `841c74d`. It is not extended to this source or to the later wording correction.

## Observed paused preview

The same owned eight-second synthetic fixture and existing temporary Linux graphical runtime were used. No source, dependency, heap option or system setting was changed for the observations.

- Mid-clip: paused source time 6.542 seconds/frame 157 remained pixel-identical immediately and at approximately 25.2 and 58.0 seconds after Rewind. The app stayed PAUSED/PREVIEW while reporting “rewound”. Play subsequently displayed 0.167 seconds/frame 4.
- ONCE end, pure preview: 7.958 seconds/frame 191 remained pixel-identical immediately and at approximately 21.4 and 66.0 seconds. Play subsequently displayed 0.208 seconds/frame 5.
- Comparisons measure the displayed source region in actual screenshots. They do not expose decoded-frame availability, texture identity or prove that the underlying seek failed.

## Unresolved termination and bounded retry

In the original live-recording trial, the last confirmed screenshot still showed PLAYING. Manual Pause was not visually confirmed. Java then disappeared and its launcher reported Killed/exit 137. The log recorded `IllegalStateException: Native object has been disposed` through `Buffer.unmap` and `Movie$NewSampleListener.newSample`. A private partial output remained; no final MP4 was published. The partial and logs were preserved for diagnosis.

Post-event memory counters had no pre-run baseline. They do not establish that this particular kill was OOM, and the callback exception does not establish its cause.

One subsequent attempt used a 75-second maximum and a conservative total-memory guard, with unchanged source/runtime/heap behaviour. The guard intentionally sent TERM at approximately 32 seconds, before output selection completed; exit was 143. Total cgroup usage was approximately 7.04 GB and Java RSS approximately 898,312 KiB at that observation. Cumulative OOM counters did not increase. Total cgroup usage includes other processes, cache and shared memory, so its growth is not a Java-heap or leak measurement. No further native retries are active.

The existing optional-plugin and X11 shutdown warnings remain. No clean-machine, physical-GPU, leak-free, cross-platform or redistribution acceptance follows.

## Bounded wording correction

The paused branch now says `Status: rewind requested; preview updates on Play`. This corrects the completion implication; it does **not** provide a fresh paused frame or repair the unresolved native termination.

Seek/pause calls, rendering and encoder policy are unchanged. Manually paused live capture retains its existing frozen-frame recording cadence. Paused ONCE-end capture remains gated until explicit Play; Rewind itself adds no encoder frames and does not clear that gate. These state invariants have automated coverage, not a completed native recording pass in this investigation.

The new 211-check harness covers paused mid/end state, manual/live controls, repeated Rewind/Play, LOOP/ONCE selection, no-video and full-process interlocks, real owned encoder counts and cleanup. The preceding source fails its wording assertion. Full automated regressions pass, but the new wording has not been re-observed through the GUI because native retries stopped after the resource guard.

## Fresh paused frame: open design item

The bundled Movie implementation queues seeking asynchronously. Its preroll listener updates metadata and disposes the sample without transferring pixels or marking Movie.available. Allowing a blind paused read is therefore not an established solution; blocking preroll retrieval on draw is unacceptable.

GStreamer's [appsink documentation](https://gstreamer.freedesktop.org/documentation/app/appsink.html) describes preroll retrieval, and [BaseSink last-sample](https://gstreamer.freedesktop.org/documentation/base/gstbasesink.html) offers a supported thumbnail-oriented sample property. A possible bridge remains unimplemented. It would need source-bound native validation, explicit sample/map/unmap/reference lifetimes, stale-sample and seek-generation checks, bounded completion, pixel-format/stride handling, resource limits and reload/disposal race tests. No reflection, speculative callback plumbing, new dependency or native ownership change is included here.

## Provenance

Tested tree: `0ed7b852a1858f0ce8b8134ac2ded7aa214d9436`. Tested `VideoGlitcher.java` SHA256: `db43ae541e1942731781ee92f62f3fe7ddcb9165267d48b6319cdcbeb63c1546`.

The detailed native report, 19 actual screenshots, three comparison/timing JSON records and checksum manifest are retained separately. This public technical checkpoint does not contain or imply publication of the raw screenshot/log/media bundle. The private evidence archive is prepared separately; its completion must be verified before claiming it is saved remotely.

Accepted detailed report SHA256: `44d2c7487255db7fc37ed36f699cb953773114974b82691a633dadb6fda3a2cd`; checksum manifest: `3b273a95f38d21b7f9464fbb2a6dcbc120a3b7f56b3f3efd720207df4726932d`. Independent evidence-review receipt: `ddfdc582c3428be3963c135e08032c35994fe579911c5300b7c8174c06297d28`.

The reviewer inspected all 19 unchanged screenshots and three JSON records, recomputed displayed-region equality and observation intervals, and checked source identities. It did not replay the native flow, independently read the raw desktop logs or decode the retained partial recording. The original report/checksum bytes remain preserved separately after one precision-only wording correction; no screenshot or comparison record was edited.
