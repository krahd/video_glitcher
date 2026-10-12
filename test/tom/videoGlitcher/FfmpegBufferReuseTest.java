package tom.videoGlitcher;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Owned pipe/process fixtures plus measured allocation; no Processing/native app. */
public final class FfmpegBufferReuseTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("glitcher-buffer-tests-");
        try {
            if (args.length > 0 && args[0].equals("--handoff-only")) emptyPollFinishHandoff(directory);
            else run(directory, args.length > 0);
        }
        finally {
            try (var paths = Files.walk(directory)) {
                for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
            }
        }
    }
    static void run(Path directory, boolean allocationOnly) throws Exception {
        measuredAllocation(directory);
        if (!allocationOnly) {
            immutableQueuedFrames(directory);
            overloadAndCancel(directory);
            failedWrite(directory);
            concurrentProducerGuard(directory);
            blockedProducerCleanup(directory);
            concurrentProducerRaces(directory);
            emptyPollFinishHandoff(directory);
            terminalRaces(directory);
            decodedFrameOrder(directory);
        }
        System.out.println("Encoder buffer reuse passed (" + checks + " assertion increments including frame waits/pixel loops; no native app/throughput claim).");
    }
    private static void measuredAllocation(Path directory) throws Exception {
        Fixture f = new Fixture(directory, "allocation", 96, 64, false);
        try {
            int[] pixels = new int[96 * 64];
            for (int i = 1; i <= 24; i++) { f.exporter.writeFrame(pixels); f.pipe.awaitWrites(i); }
            var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
            check(bean.isThreadAllocatedMemorySupported(), "Owned allocation counter required");
            if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
            long id = Thread.currentThread().threadId();
            long before = bean.getThreadAllocatedBytes(id);
            for (int i = 25; i <= 264; i++) { f.exporter.writeFrame(pixels); f.pipe.awaitWrites(i); }
            long bytes = bean.getThreadAllocatedBytes(id) - before;
            long clonePayload = 240L * 96 * 64 * 3;
            System.out.println("Owned producer allocation: " + bytes + " bytes for 240 paced frames; per-frame clone payload baseline " + clonePayload + " bytes; writer identities " + f.pipe.identities.size());
            check(bytes >= 0 && bytes < clonePayload / 2, "Paced producer must avoid full RGB allocation per frame");
            check(f.pipe.identities.size() <= 3, "Paced writer must reuse a small set of frame identities");
            check(number(f.exporter, "allocatedFrameBuffers") <= number(f.exporter, "frameBufferCapacity"), "Array construction count bounded");
            f.exporter.finish();
            check(number(f.exporter, "retainedFrameBuffers") == 0, "Finish releases pool ownership");
        } finally { f.close(); }
    }
    private static void immutableQueuedFrames(Path directory) throws Exception {
        Fixture f = new Fixture(directory, "immutable", 5, 3, true);
        try {
            int[] pixels = new int[15];
            int[] colours = {0xffff0000, 0xff0000ff, 0xff00ff00, 0xffffffff};
            Arrays.fill(pixels, colours[0]); f.exporter.writeFrame(pixels); f.pipe.awaitStarted();
            byte[] held = f.pipe.held;
            for (int i = 1; i < colours.length; i++) { Arrays.fill(pixels, colours[i]); f.exporter.writeFrame(pixels); }
            Arrays.fill(pixels, 0xff123456);
            check((held[0] & 255) == 255 && held[1] == 0 && held[2] == 0, "Consumer-owned red buffer is never overwritten");
            f.pipe.release.countDown(); f.exporter.finish();
            check(f.pipe.frames.size() == colours.length, "All accepted frames retained in order");
            for (int i = 0; i < colours.length; i++) {
                byte[] bytes = f.pipe.frames.get(i);
                check(bytes.length == 4 * 2 * 3, "Odd dimensions retain even crop");
                for (int p = 0; p < bytes.length; p += 3)
                    check((bytes[p] & 255) == ((colours[i] >> 16) & 255) && (bytes[p+1] & 255) == ((colours[i] >> 8) & 255) && (bytes[p+2] & 255) == (colours[i] & 255), "Queued pixels remain immutable and ordered");
            }
            check(number(f.exporter, "retainedFrameBuffers") == 0, "Successful terminal state retains no pool arrays");
        } finally { f.close(); }
    }
    private static void overloadAndCancel(Path directory) throws Exception {
        Fixture f = new Fixture(directory, "overflow", 4, 4, true);
        try {
            int[] pixels = new int[16];
            f.exporter.writeFrame(pixels); f.pipe.awaitStarted();
            int queuedCapacity = 48;
            for (int i = 0; i < queuedCapacity; i++) f.exporter.writeFrame(pixels);
            try { f.exporter.writeFrame(pixels); throw new AssertionError("Overflow accepted"); }
            catch (IOException expected) { check(expected.getMessage().contains("cannot keep up"), "Overload remains explicit"); }
            check(f.exporter.framesWritten() == 49, "Rejected overload is not counted/dropped silently");
            check(number(f.exporter, "allocatedFrameBuffers") <= queuedCapacity + 2, "Pending + producer + consumer is the array bound");
            check(f.exporter.abort(), "Abort remains accepted before publication");
            check(f.exporter.awaitCleanup(10, TimeUnit.SECONDS), "Cancelled writer returns ownership");
            check(number(f.exporter, "retainedFrameBuffers") == 0, "Cancel reclaims free, queued and in-flight arrays");
            check(!Files.exists(f.output), "Cancellation never publishes");
        } finally { f.close(); }
    }
    private static void failedWrite(Path directory) throws Exception {
        Fixture f = new Fixture(directory, "write-failure", 4, 4, false);
        try {
            f.pipe.failAt = 2;
            f.exporter.writeFrame(new int[16]); f.pipe.awaitWrites(1);
            f.exporter.writeFrame(new int[16]);
            try { f.exporter.finish(); throw new AssertionError("Failed write saved"); }
            catch (IOException expected) { checks++; }
            check(number(f.exporter, "retainedFrameBuffers") == 0, "Failed write releases borrowed array and queued pool");
            check(!Files.exists(f.output), "Failed write has no final output");
        } finally { f.close(); }
    }
    private static void concurrentProducerGuard(Path directory) throws Exception {
        Fixture f = new Fixture(directory, "producer-guard", 4, 4, false);
        try {
            Field field = FfmpegVideoExporter.class.getDeclaredField("producerActive"); field.setAccessible(true);
            field.setBoolean(f.exporter, true); // Owned isolated model of another producer retaining its lease.
            try { f.exporter.writeFrame(new int[16]); throw new AssertionError("Concurrent producer accepted"); }
            catch (IOException expected) { check(expected.getMessage().contains("already being prepared"), "Concurrent producers are explicitly rejected"); }
            finally { field.setBoolean(f.exporter, false); }
            check(f.exporter.framesWritten() == 0, "Rejected producer cannot change accepted count");
            f.exporter.writeFrame(new int[16]); f.exporter.finish();
        } finally { f.close(); }
    }
    private static void terminalRaces(Path directory) throws Exception {
        for (boolean cancel : new boolean[]{false, true}) for (int iteration = 0; iteration < 40; iteration++) {
            Fixture f = new Fixture(directory, "race-" + cancel + "-" + iteration, 64, 48, false);
            try {
                CyclicBarrier start = new CyclicBarrier(3);
                AtomicReference<Throwable> unexpected = new AtomicReference<>();
                Thread producer = new Thread(() -> {
                    try { start.await(); f.exporter.writeFrame(new int[64 * 48]); }
                    catch (IOException expected) { /* Closing wins the handoff. */ }
                    catch (Throwable failure) { unexpected.set(failure); }
                });
                Thread terminator = new Thread(() -> {
                    try { start.await(); if (cancel) f.exporter.abort(); else f.exporter.finish(); }
                    catch (IOException expected) { /* An empty export is correctly refused. */ }
                    catch (Throwable failure) { unexpected.set(failure); }
                });
                producer.start(); terminator.start(); start.await(); producer.join(5000); terminator.join(5000);
                check(!producer.isAlive() && !terminator.isAlive(), "Producer/terminal race must finish");
                if (unexpected.get() != null) throw new AssertionError("Unexpected terminal race", unexpected.get());
                check(f.exporter.awaitCleanup(10, TimeUnit.SECONDS), "Raced cleanup completes");
                check(number(f.exporter, "retainedFrameBuffers") == 0, "Race releases every owned array");
                if (cancel) check(!Files.exists(f.output), "Cancelled race cannot publish");
                else {
                    check(f.pipe.frames.size() == f.exporter.framesWritten(), "Finish cannot lose an accepted handoff");
                    check(Files.exists(f.output) == (f.exporter.framesWritten() > 0), "Only nonempty accepted race publishes");
                }
            } finally { f.close(); }
        }
    }
    private static void blockedProducerCleanup(Path directory) throws Exception {
        Fixture f = new Fixture(directory, "blocked-producer", 4, 4, false);
        Field owner = FfmpegVideoExporter.class.getDeclaredField("frameBuffers"); owner.setAccessible(true);
        Field active = FfmpegVideoExporter.class.getDeclaredField("producerActive"); active.setAccessible(true);
        Object pool = owner.get(f.exporter);
        Method acquire = pool.getClass().getDeclaredMethod("acquire"); acquire.setAccessible(true);
        Method release = pool.getClass().getDeclaredMethod("release", byte[].class); release.setAccessible(true);
        Method awaitProducer = FfmpegVideoExporter.class.getDeclaredMethod("awaitProducerStopped", long.class); awaitProducer.setAccessible(true);
        byte[] leased = null;
        try {
            synchronized (pool) { leased = (byte[]) acquire.invoke(pool); active.setBoolean(f.exporter, true); }
            check(!(Boolean) awaitProducer.invoke(f.exporter, System.nanoTime() - 1), "Expired producer deadline is not a release");
            f.exporter.abort();
            check(!f.exporter.awaitCleanup(30, TimeUnit.MILLISECONDS), "Cleanup waits for the owned active producer");
            check(number(f.exporter, "retainedFrameBuffers") == 1, "A stalled owner is not falsely reclaimed or recycled");
            try { acquire.invoke(pool); throw new AssertionError("Closed pool lent another buffer"); }
            catch (InvocationTargetException expected) { check(expected.getCause() instanceof IOException, "Closed pool refuses acquisition"); }
        } finally {
            synchronized (pool) {
                if (leased != null) release.invoke(pool, (Object) leased);
                active.setBoolean(f.exporter, false); pool.notifyAll();
            }
            check(f.exporter.awaitCleanup(5, TimeUnit.SECONDS), "Cleanup finishes when the owner actually returns");
            check(number(f.exporter, "retainedFrameBuffers") == 0, "Returned terminal lease is discarded");
            f.close();
        }
    }
    private static void emptyPollFinishHandoff(Path directory) throws Exception {
        Fixture f = new Fixture(directory, "empty-poll-handoff", 4, 4, false);
        EmptyPollQueue queue = new EmptyPollQueue();
        try {
            // An owned queue pauses exactly after a timed poll has found no frame,
            // but before the consumer checks FINISHING. No production test hook.
            Field pending = FfmpegVideoExporter.class.getDeclaredField("pendingFrames"); pending.setAccessible(true);
            pending.set(f.exporter, queue);
            check(queue.empty.await(5, TimeUnit.SECONDS), "Consumer reached an empty poll");
            f.exporter.writeFrame(new int[16]);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread finish = new Thread(() -> { try { f.exporter.finish(); } catch (Throwable error) { failure.set(error); } });
            finish.start();
            Field state = FfmpegVideoExporter.class.getDeclaredField("state"); state.setAccessible(true);
            AtomicReference<?> value = (AtomicReference<?>) state.get(f.exporter);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!value.get().toString().equals("FINISHING") && System.nanoTime() < deadline) Thread.sleep(1);
            check(value.get().toString().equals("FINISHING"), "Finish closed the handoff gate");
            queue.release.countDown(); finish.join(5000);
            check(!finish.isAlive() && failure.get() == null, "Handoff finish completes successfully");
            check(f.pipe.frames.size() == 1 && f.exporter.framesWritten() == 1, "Finish must consume a frame accepted after the last empty poll");
            check(number(f.exporter, "retainedFrameBuffers") == 0, "Empty-poll race leaves no retained frame");
        } finally { queue.release.countDown(); f.close(); }
    }
    private static void concurrentProducerRaces(Path directory) throws Exception {
        int accepted = 0;
        int rejected = 0;
        for (int iteration = 0; iteration < 20; iteration++) {
            Fixture f = new Fixture(directory, "parallel-" + iteration, 128, 96, false);
            try {
                int[] colours = {0xffff0000, 0xff0000ff, 0xff00ff00, 0xffffffff};
                boolean[] saved = new boolean[4];
                CyclicBarrier start = new CyclicBarrier(5);
                AtomicReference<Throwable> failure = new AtomicReference<>();
                Thread[] producers = new Thread[4];
                for (int i = 0; i < 4; i++) {
                    final int index = i;
                    producers[i] = new Thread(() -> {
                        try {
                            int[] pixels = new int[128 * 96]; Arrays.fill(pixels, colours[index]);
                            start.await(); f.exporter.writeFrame(pixels); saved[index] = true;
                        } catch (IOException expected) {
                            if (!expected.getMessage().contains("already being prepared")) failure.set(expected);
                        } catch (Throwable error) { failure.set(error); }
                    });
                    producers[i].start();
                }
                start.await();
                for (Thread producer : producers) { producer.join(5000); check(!producer.isAlive(), "Concurrent producer finishes or rejects"); }
                if (failure.get() != null) throw new AssertionError("Concurrent producer failure", failure.get());
                f.exporter.finish();
                Set<Integer> wanted = new HashSet<>();
                for (int i = 0; i < 4; i++) { if (saved[i]) { wanted.add(colours[i]); accepted++; } else rejected++; }
                check(f.pipe.frames.size() == wanted.size(), "Each accepted producer transfers exactly one frame");
                for (byte[] bytes : f.pipe.frames) {
                    int colour = 0xff000000 | (bytes[0] & 255) << 16 | (bytes[1] & 255) << 8 | (bytes[2] & 255);
                    check(wanted.remove(colour), "Received frame belongs to one accepted producer");
                    for (int p = 0; p < bytes.length; p += 3)
                        if (bytes[p] != bytes[0] || bytes[p+1] != bytes[1] || bytes[p+2] != bytes[2]) throw new AssertionError("Concurrent producers mixed pixels");
                }
                check(wanted.isEmpty() && number(f.exporter, "retainedFrameBuffers") == 0, "Concurrent handoffs retain no arrays or missing frames");
            } finally { f.close(); }
        }
        System.out.println("Owned concurrent-producer stress: " + accepted + " accepted, " + rejected + " explicitly rejected; scheduling-dependent counts, no mixed frames.");
    }
    private static void decodedFrameOrder(Path directory) throws Exception {
        Path output = directory.resolve("buffer-order.mp4");
        FfmpegVideoExporter exporter = FfmpegVideoExporter.start(output.toString(), 65, 49, 24);
        int[] pixels = new int[65 * 49];
        int[] colours = {0xffff0000, 0xff0000ff, 0xff00ff00, 0xffffffff};
        try {
            for (int i = 0; i < 32; i++) {
                Arrays.fill(pixels, colours[i % 4]);
                for (int y = 0; y < 49; y++) pixels[y * 65 + 64] = 0xff123456;
                Arrays.fill(pixels, 48 * 65, pixels.length, 0xff123456);
                exporter.writeFrame(pixels);
            }
            Arrays.fill(pixels, 0xff000000); exporter.finish();
            Process decoder = new ProcessBuilder("ffmpeg", "-v", "error", "-i", output.toString(), "-f", "rawvideo", "-pix_fmt", "rgb24", "-").redirectError(ProcessBuilder.Redirect.INHERIT).start();
            byte[] decoded = decoder.getInputStream().readAllBytes();
            check(decoder.waitFor(20, TimeUnit.SECONDS) && decoder.exitValue() == 0, "Owned encoded output decodes");
            int frameBytes = 64 * 48 * 3;
            check(decoded.length == 32 * frameBytes, "Decoded frame count and cropped dimensions exact");
            for (int i = 0; i < 32; i++) for (int c = 0; c < 3; c++) {
                int wanted = (colours[i % 4] >> (16 - c * 8)) & 255;
                for (int p = 0; p < frameBytes; p += 3)
                    if (Math.abs((decoded[i * frameBytes + p + c] & 255) - wanted) > 12)
                        throw new AssertionError("Decoded order/colour/crop changed at frame " + i);
                checks++;
            }
            check(number(exporter, "retainedFrameBuffers") == 0, "Real encode releases pool");
        } finally { exporter.abort(); exporter.awaitCleanup(10, TimeUnit.SECONDS); }
    }
    private static int number(FfmpegVideoExporter exporter, String method) throws Exception {
        Method m = FfmpegVideoExporter.class.getDeclaredMethod(method); m.setAccessible(true);
        return (Integer) m.invoke(exporter);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }

    private static final class Fixture implements AutoCloseable {
        final Path output;
        final Path temporary;
        final Pipe pipe;
        final FfmpegVideoExporter exporter;
        Fixture(Path directory, String name, int width, int height, boolean blocked) throws Exception {
            output = directory.resolve(name + ".mp4"); temporary = Files.createTempFile(directory, ".owned-buffer-", ".part");
            pipe = new Pipe(temporary, blocked); OwnedProcess process = new OwnedProcess(pipe); pipe.process = process;
            Constructor<FfmpegVideoExporter> constructor = FfmpegVideoExporter.class.getDeclaredConstructor(Process.class, FfmpegVideoExporter.FrameSpec.class, Path.class, Path.class);
            constructor.setAccessible(true);
            exporter = constructor.newInstance(process, FfmpegVideoExporter.frameSpecFor(width, height), output, temporary);
        }
        public void close() throws Exception {
            exporter.abort(); pipe.release.countDown(); exporter.awaitCleanup(10, TimeUnit.SECONDS);
            Files.deleteIfExists(output); Files.deleteIfExists(temporary);
        }
    }
    private static final class Pipe extends OutputStream {
        final Path temporary;
        final CountDownLatch release;
        final CountDownLatch started = new CountDownLatch(1);
        final List<byte[]> frames = new ArrayList<>();
        final Set<byte[]> identities = Collections.newSetFromMap(new IdentityHashMap<>());
        volatile byte[] held;
        volatile boolean aborted;
        volatile int failAt = -1;
        OwnedProcess process;
        Pipe(Path temporary, boolean blocked) { this.temporary = temporary; release = new CountDownLatch(blocked ? 1 : 0); }
        public void write(int ignored) { throw new AssertionError("Expected complete frame write"); }
        public void write(byte[] bytes, int offset, int length) throws IOException {
            held = bytes; started.countDown();
            try { if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("Owned pipe deadline"); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Owned pipe interrupted", e); }
            synchronized (this) {
                if (aborted || frames.size() + 1 == failAt) { process.exited.countDown(); throw new IOException("Owned write failure"); }
                identities.add(bytes); frames.add(Arrays.copyOfRange(bytes, offset, offset + length)); notifyAll();
            }
        }
        synchronized void awaitWrites(int count) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (frames.size() < count && System.nanoTime() < deadline) TimeUnit.NANOSECONDS.timedWait(this, Math.max(1, deadline - System.nanoTime()));
            check(frames.size() >= count, "Owned writer advances");
        }
        void awaitStarted() throws Exception { check(started.await(5, TimeUnit.SECONDS), "Owned consumer acquired first buffer"); }
        public synchronized void close() throws IOException {
            if (!aborted) Files.writeString(temporary, "owned pipe fixture, not media");
            process.exited.countDown();
        }
        synchronized void abort() { aborted = true; release.countDown(); process.exited.countDown(); }
    }
    private static final class OwnedProcess extends Process {
        final Pipe pipe;
        final CountDownLatch exited = new CountDownLatch(1);
        OwnedProcess(Pipe pipe) { this.pipe = pipe; }
        public OutputStream getOutputStream() { return pipe; }
        public InputStream getInputStream() { return InputStream.nullInputStream(); }
        public InputStream getErrorStream() { return InputStream.nullInputStream(); }
        public int waitFor() throws InterruptedException { exited.await(); return 0; }
        public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException { return exited.await(timeout, unit); }
        public int exitValue() { if (isAlive()) throw new IllegalThreadStateException(); return 0; }
        public void destroy() { pipe.abort(); }
        public Process destroyForcibly() { destroy(); return this; }
        public boolean isAlive() { return exited.getCount() != 0; }
        public java.util.stream.Stream<ProcessHandle> descendants() { return java.util.stream.Stream.empty(); }
    }
    private static final class EmptyPollQueue extends ArrayBlockingQueue<byte[]> {
        final CountDownLatch empty = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        private boolean first = true;
        EmptyPollQueue() { super(48); }
        public byte[] poll(long timeout, TimeUnit unit) throws InterruptedException {
            if (first) {
                first = false; empty.countDown();
                if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("Owned empty-poll deadline");
                return null;
            }
            return super.poll(timeout, unit);
        }
    }
}
