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

import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.CefSettings;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Starts CEF from the downloaded natives. Internal; use {@link MCEF}.
 */
final class CefUtil {
    private CefUtil() {
    }

    private static boolean init;
    private static CefApp cefAppInstance;
    private static CefClient cefClientInstance;

    private static void setUnixExecutable(File file) {
        if (!file.exists()) return;
        try {
            Files.setPosixFilePermissions(file.toPath(), EnumSet.of(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
        } catch (IOException | UnsupportedOperationException e) {
            MCEF.getLogger().error("Failed to set {} as executable", file, e);
        }
    }

    static boolean init(Path platformDir, Path cacheDir) {
        MCEFPlatform platform = MCEFPlatform.getPlatform();
        File dir = platformDir.toFile();

        // the archive keeps the executable bits, but be sure (some unpackers / file systems drop them)
        if (platform.isLinux()) {
            setUnixExecutable(new File(dir, "jcef_helper"));
        } else if (platform.isMacOS()) {
            String fw = "jcef_app.app/Contents/Frameworks/";
            setUnixExecutable(new File(dir, fw + "jcef Helper.app/Contents/MacOS/jcef Helper"));
            setUnixExecutable(new File(dir, fw + "jcef Helper (GPU).app/Contents/MacOS/jcef Helper (GPU)"));
            setUnixExecutable(new File(dir, fw + "jcef Helper (Plugin).app/Contents/MacOS/jcef Helper (Plugin)"));
            setUnixExecutable(new File(dir, fw + "jcef Helper (Renderer).app/Contents/MacOS/jcef Helper (Renderer)"));
        }

        MCEFSettings settings = MCEF.getSettings();
        List<String> switches = new ArrayList<>();
        // videos must start without a click
        switches.add("--autoplay-policy=no-user-gesture-required");
        if (platform.isLinux()) {
            // Chromium draws through ANGLE on GL and runs on X11 (XWayland on Wayland sessions)
            switches.add("--use-angle=gl");
            switches.add("--ozone-platform=x11");
            // Chromium's memory log reads glibc's int-sized mallinfo(), which goes negative once the game's native
            // heap passes 2 GiB, and Chromium then kills the process (chromiumembedded/cef#3963)
            switches.add("--test-memory-log-delay-in-minutes=525600");
        }
        boolean verbose = Boolean.getBoolean("ytscreen.chromium.verbose");
        if (verbose) {
            switches.add("--enable-logging=stderr");
            switches.add("--v=1");
        }
        for (String s : settings.getExtraSwitches()) {
            String name = s.contains("=") ? s.substring(0, s.indexOf('=')) : s;
            switches.removeIf(x -> x.equals(name) || x.startsWith(name + "="));
            switches.add(s);
        }
        String[] cefSwitches = switches.toArray(new String[0]);

        System.setProperty("jcef.path", dir.getAbsolutePath());
        MCEF.getLogger().info("Chromium switches: {}", String.join(" ", cefSwitches));
        if (!CefApp.startup(cefSwitches)) {
            return false;
        }

        CefSettings cefSettings = new CefSettings();
        cefSettings.windowless_rendering_enabled = true;
        // CEF 120+: one process-singleton lock per root_cache_path; the profile (cookies, sign-ins) lives inside it
        Path root = cacheDir.toAbsolutePath().getParent();
        try {
            Files.createDirectories(cacheDir);
        } catch (IOException e) {
            MCEF.getLogger().warn("Could not create {}", cacheDir, e);
        }
        cefSettings.root_cache_path = root.toString();
        if (settings.isUsingCache()) cefSettings.cache_path = cacheDir.toAbsolutePath().toString();
        cefSettings.background_color = cefSettings.new ColorType(0, 255, 255, 255);
        cefSettings.log_file = cacheDir.getParent().resolve("chromium.log").toAbsolutePath().toString();
        cefSettings.log_severity = verbose ? CefSettings.LogSeverity.LOGSEVERITY_VERBOSE : CefSettings.LogSeverity.LOGSEVERITY_WARNING;
        if (!settings.getUserAgent().isEmpty()) {
            cefSettings.user_agent = settings.getUserAgent();
        } else {
            // Work around for Google sign-in "This browser or app may not be secure."
            cefSettings.user_agent_product = "MCEF/2";
        }

        cefAppInstance = CefApp.getInstance(cefSwitches, cefSettings);
        cefClientInstance = cefAppInstance.createClient();
        return init = true;
    }

    static void shutdown() {
        if (init) {
            init = false;
            try {
                cefClientInstance.dispose();
            } catch (Exception e) {
                MCEF.getLogger().error("Failed to dispose the CEF client", e);
            }
            try {
                cefAppInstance.dispose();
            } catch (Exception e) {
                MCEF.getLogger().error("Failed to dispose CEF", e);
            }
        }
    }

    static boolean isInit() {
        return init;
    }

    static CefApp getCefApp() {
        return cefAppInstance;
    }

    static CefClient getCefClient() {
        return cefClientInstance;
    }
}
