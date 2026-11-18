/*
 *     YouTube Screen Chromium - a fork of MCEF (Minecraft Chromium Embedded Framework)
 *     Copyright (C) 2023 CinemaMod Group
 *     Copyright (C) 2025 CCBlueX
 *     Copyright (C) 2026 Lumi-Kodo
 *
 *     This library is free software; you can redistribute it and/or
 *     modify it under the terms of the GNU Lesser General Public
 *     License as published by the Free Software Foundation; either
 *     version 2.1 of the License, or (at your option) any later version.
 *
 *     This library is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *     Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public
 *     License along with this library; if not, write to the Free Software
 *     Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 *     USA
 */

package com.ytscreen.chromium;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.fml.loading.FMLPaths;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

/**
 * Downloads and installs the Chromium natives for this platform into {@code <game>/ytscreen-chromium/natives}.
 * <ul>
 *   <li>The archive comes from this fork's GitHub release for the java-cef commit compiled into the jar.</li>
 *   <li>Its SHA-256 must match the checksum built into the jar (natives.sha256), so a changed or broken download is
 *       never executed. After unpacking, every file is checked against the archive's own file list (.hash).</li>
 *   <li>Unpacking cannot write outside the target directory (no "zip slip").</li>
 * </ul>
 * Runs on its own thread; the state is read by the progress overlay and by {@link MCEF}.
 */
public final class Natives {
    public enum State {
        CHECKING, DOWNLOADING, EXTRACTING, VERIFYING, READY, FAILED
    }

    private static volatile State state = State.CHECKING;
    private static volatile float progress;
    private static volatile String error = "";
    private static volatile Path platformDir;
    private static Thread thread;

    private Natives() {
    }

    public static State state() {
        return state;
    }

    /** 0..1 within the current state. */
    public static float progress() {
        return progress;
    }

    public static String error() {
        return error;
    }

    public static Path root() {
        return FMLPaths.GAMEDIR.get().resolve("ytscreen-chromium");
    }

    static Path platformDir() {
        return platformDir;
    }

    public static synchronized void start() {
        if (thread != null) return;
        thread = new Thread(Natives::run, "YTS-Chromium-Natives");
        thread.setDaemon(true);
        thread.start();
    }

    private static void fail(String message, Throwable t) {
        error = message;
        state = State.FAILED;
        if (t != null) MCEF.getLogger().error("Chromium natives: {}", message, t);
        else MCEF.getLogger().error("Chromium natives: {}", message);
    }

    private static String resource(String name) throws IOException {
        try (InputStream in = Natives.class.getClassLoader().getResourceAsStream(name)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static String javaCefCommit() {
        try {
            String c = resource("jcef.commit");
            return c == null ? null : c.trim();
        } catch (IOException e) {
            return null;
        }
    }

    private static void run() {
        try {
            MCEFPlatform platform;
            try {
                platform = MCEFPlatform.getPlatform();
            } catch (RuntimeException e) {
                fail("this operating system or processor is not supported: " + System.getProperty("os.name") + " " + System.getProperty("os.arch"), null);
                return;
            }
            String name = platform.getNormalizedName();

            // development: natives of a java-cef build of your own
            String provided = System.getProperty("ytscreen.chromium.natives");
            if (provided != null && !provided.isEmpty()) {
                platformDir = Path.of(provided).toAbsolutePath();
                MCEF.getLogger().info("Using provided Chromium natives at {}", platformDir);
                state = State.READY;
                return;
            }

            String commit = javaCefCommit();
            if (commit == null || !commit.matches("[0-9a-f]{40}")) {
                fail("jcef.commit is missing from the mod jar", null);
                return;
            }
            Properties sums = new Properties();
            String sumText = resource("natives.sha256");
            if (sumText != null) sums.load(new java.io.StringReader(sumText));
            String expected = sums.getProperty(name, "").trim();
            if (!expected.matches("[0-9a-f]{64}")) {
                fail("this build of the mod has no Chromium for " + name + " yet, please update the mod", null);
                return;
            }

            Path nativesRoot = root().resolve("natives");
            Path target = nativesRoot.resolve(commit.substring(0, 12));
            Path marker = target.resolve(".installed-" + name);
            platformDir = target.resolve(name);
            if (Files.isRegularFile(marker) && Files.readString(marker).trim().equals(expected) && Files.isDirectory(platformDir)) {
                MCEF.getLogger().info("Chromium natives {} ({}) already installed", name, commit.substring(0, 12));
                state = State.READY;
                cleanupOld(nativesRoot, target);
                return;
            }

            String repo = resource("natives.repo");
            String url = MCEF.getSettings().getDownloadUrl()
                    .replace("${repo}", repo == null || repo.isBlank() ? "Lumi-Kodo/ytscreen-chromium" : repo.trim())
                    .replace("${commit}", commit)
                    .replace("${platform}", name);
            Files.createDirectories(nativesRoot);
            Path archive = nativesRoot.resolve(name + "-" + commit.substring(0, 12) + ".tar.gz");
            download(url, archive, expected);

            state = State.EXTRACTING;
            progress = 0;
            deleteTree(target);
            Files.createDirectories(target);
            extract(archive, target);

            state = State.VERIFYING;
            progress = 0;
            verifyFileList(target);

            Files.writeString(marker, expected);
            Files.deleteIfExists(archive);
            cleanupOld(nativesRoot, target);
            MCEF.getLogger().info("Chromium natives {} ({}) installed", name, commit.substring(0, 12));
            state = State.READY;
        } catch (Throwable t) {
            fail(t.getMessage() == null ? t.toString() : t.getMessage(), t);
        }
    }

    // ------------------------------------------------------------------ download

    private static void download(String url, Path archive, String expectedSha256) throws Exception {
        HttpClient http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        Exception last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            state = State.DOWNLOADING;
            progress = 0;
            Path part = archive.resolveSibling(archive.getFileName() + ".part");
            try {
                MCEF.getLogger().info("Downloading Chromium natives (attempt {}): {}", attempt, url);
                HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(30))
                        .header("User-Agent", "ytscreen-chromium").GET().build();
                HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
                if (resp.statusCode() != 200) {
                    resp.body().close();
                    throw new IOException("HTTP " + resp.statusCode() + " for " + url);
                }
                long total = resp.headers().firstValueAsLong("Content-Length").orElse(-1);
                MessageDigest sha = MessageDigest.getInstance("SHA-256");
                try (InputStream in = new DigestInputStream(new BufferedInputStream(resp.body(), 1 << 16), sha);
                     OutputStream out = Files.newOutputStream(part)) {
                    byte[] buf = new byte[1 << 16];
                    long done = 0;
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        out.write(buf, 0, n);
                        done += n;
                        if (total > 0) progress = (float) done / total;
                    }
                }
                String actual = HexFormat.of().formatHex(sha.digest());
                if (!actual.equals(expectedSha256)) {
                    throw new IOException("checksum mismatch (expected " + expectedSha256 + ", got " + actual + ")");
                }
                Files.move(part, archive, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (Exception e) {
                last = e;
                Files.deleteIfExists(part);
                MCEF.getLogger().warn("Chromium download attempt {} failed: {}", attempt, e.toString());
                if (attempt < 3) Thread.sleep(3000L * attempt);
            }
        }
        throw new IOException("download failed: " + (last == null ? "?" : last.getMessage()), last);
    }

    // ------------------------------------------------------------------ unpacking

    private static void extract(Path archive, Path target) throws IOException {
        long size = Math.max(1, Files.size(archive));
        Path base = target.toAbsolutePath().normalize();
        try (InputStream fileIn = Files.newInputStream(archive);
             CountingStream counted = new CountingStream(new BufferedInputStream(fileIn, 1 << 16));
             TarArchiveInputStream tar = new TarArchiveInputStream(new GZIPInputStream(counted, 1 << 16))) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                progress = Math.min(1f, (float) counted.count / size);
                Path out = base.resolve(entry.getName()).normalize();
                if (!out.startsWith(base)) throw new IOException("unsafe path in archive: " + entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                    continue;
                }
                Files.createDirectories(out.getParent());
                if (entry.isSymbolicLink()) {
                    Path link = Path.of(entry.getLinkName());
                    Path resolved = out.getParent().resolve(link).normalize();
                    if (link.isAbsolute() || !resolved.startsWith(base)) throw new IOException("unsafe link in archive: " + entry.getName());
                    Files.deleteIfExists(out);
                    Files.createSymbolicLink(out, link);
                    continue;
                }
                if (entry.isLink()) {
                    Path source = base.resolve(entry.getLinkName()).normalize();
                    if (!source.startsWith(base)) throw new IOException("unsafe link in archive: " + entry.getName());
                    Files.copy(source, out, StandardCopyOption.REPLACE_EXISTING);
                    continue;
                }
                if (!entry.isFile()) continue;
                Files.copy(tar, out, StandardCopyOption.REPLACE_EXISTING);
                if ((entry.getMode() & 0111) != 0) out.toFile().setExecutable(true, false);
            }
        }
        progress = 1f;
    }

    /** Checks every unpacked file against the archive's .hash list (sha256 per file). */
    private static void verifyFileList(Path target) throws Exception {
        Path list = target.resolve(".hash");
        if (!Files.isRegularFile(list)) throw new IOException("the archive has no file list");
        JsonObject hashes = JsonParser.parseString(Files.readString(list)).getAsJsonObject();
        int total = Math.max(1, hashes.size()), i = 0;
        Path base = target.toAbsolutePath().normalize();
        for (Map.Entry<String, com.google.gson.JsonElement> e : hashes.entrySet()) {
            progress = (float) i++ / total;
            Path f = base.resolve(e.getKey()).normalize();
            if (!f.startsWith(base) || !Files.isRegularFile(f)) throw new IOException("missing file " + e.getKey());
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new DigestInputStream(new BufferedInputStream(Files.newInputStream(f), 1 << 16), sha)) {
                in.transferTo(OutputStream.nullOutputStream());
            }
            if (!HexFormat.of().formatHex(sha.digest()).equals(e.getValue().getAsString())) throw new IOException("damaged file " + e.getKey());
        }
        progress = 1f;
    }

    private static void cleanupOld(Path nativesRoot, Path keep) {
        try (Stream<Path> s = Files.list(nativesRoot)) {
            s.filter(p -> !p.equals(keep)).forEach(p -> {
                try {
                    deleteTree(p);
                } catch (IOException e) {
                    MCEF.getLogger().debug("Could not delete old natives {}", p, e);
                }
            });
        } catch (IOException ignored) {
        }
    }

    private static void deleteTree(Path p) throws IOException {
        if (!Files.exists(p, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return;
        if (!Files.isDirectory(p, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            Files.delete(p);
            return;
        }
        try (Stream<Path> s = Files.walk(p)) {
            for (Path x : s.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(x);
        }
    }

    private static final class CountingStream extends java.io.FilterInputStream {
        long count;

        CountingStream(InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) count++;
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n > 0) count += n;
            return n;
        }
    }
}
