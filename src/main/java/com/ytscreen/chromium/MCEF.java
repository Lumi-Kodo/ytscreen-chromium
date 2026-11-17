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

import com.ytscreen.chromium.listeners.MCEFInitListener;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * The API for creating Chromium browsers in Minecraft (same shape as MCEF's, under a new package so both mods can be
 * installed side by side).
 * <p>
 * Chromium starts on the render thread once the natives are installed (see {@link Natives}); use
 * {@link #isInitialized()} or {@link #scheduleForInit(MCEFInitListener)} before creating browsers.
 */
public final class MCEF {
    public static final Logger LOGGER = LoggerFactory.getLogger("YTS-Chromium");
    private static MCEFSettings settings;
    private static MCEFApp app;
    private static MCEFClient client;
    private static boolean failed;
    private static final List<MCEFInitListener> awaitingInit = new ArrayList<>();

    private MCEF() {
    }

    public static Logger getLogger() {
        return LOGGER;
    }

    public static synchronized MCEFSettings getSettings() {
        if (settings == null) {
            settings = new MCEFSettings();
            settings.load();
        }
        return settings;
    }

    /** Runs the task when Chromium is ready (immediately if it already is), or with {@code false} if it failed. */
    public static void scheduleForInit(MCEFInitListener task) {
        if (isInitialized()) task.onInit(true);
        else if (failed) task.onInit(false);
        else awaitingInit.add(task);
    }

    /** True if Chromium could not be started (natives missing or broken, unsupported platform). */
    public static boolean hasFailed() {
        return failed || Natives.state() == Natives.State.FAILED;
    }

    /** Called by the mod on the render thread when the natives are ready. */
    static boolean initialize() {
        if (isInitialized() || failed) return isInitialized();
        LOGGER.info("Starting Chromium on {}...", MCEFPlatform.getPlatform().getNormalizedName());
        boolean ok;
        try {
            ok = CefUtil.init(Natives.platformDir(), Natives.root().resolve("cache"));
        } catch (Throwable t) {
            LOGGER.error("Chromium could not be started", t);
            ok = false;
        }
        if (ok) {
            app = new MCEFApp(CefUtil.getCefApp());
            client = new MCEFClient(CefUtil.getCefClient());
            LOGGER.info("Chromium is ready (java-cef {})", Natives.javaCefCommit());
            // the browser processes must not outlive the game; macOS asks the app to terminate instead
            MCEFPlatform platform = MCEFPlatform.getPlatform();
            if (platform.isMacOS()) {
                CefUtil.getCefApp().macOSTerminationRequestRunnable = () -> {
                    shutdown();
                    Minecraft.getInstance().stop();
                };
            } else {
                Runtime.getRuntime().addShutdownHook(new Thread(MCEF::shutdown, "YTS-Chromium-Shutdown"));
            }
        } else {
            failed = true;
            LOGGER.error("Could not start Chromium");
            CefUtil.shutdown();
        }
        List<MCEFInitListener> tasks = new ArrayList<>(awaitingInit);
        awaitingInit.clear();
        for (MCEFInitListener t : tasks) {
            try {
                t.onInit(ok);
            } catch (Throwable e) {
                LOGGER.error("Init listener failed", e);
            }
        }
        return ok;
    }

    public static MCEFApp getApp() {
        assertInitialized();
        return app;
    }

    public static MCEFClient getClient() {
        assertInitialized();
        return client;
    }

    /** Creates a browser (render thread). */
    public static MCEFBrowser createBrowser(String url, boolean transparent) {
        assertInitialized();
        MCEFBrowser browser = new MCEFBrowser(client, url, transparent);
        browser.setCloseAllowed();
        browser.createImmediately();
        return browser;
    }

    public static MCEFBrowser createBrowser(String url, boolean transparent, int width, int height) {
        MCEFBrowser browser = createBrowser(url, transparent);
        browser.resize(width, height);
        return browser;
    }

    public static boolean isInitialized() {
        return client != null;
    }

    public static synchronized void shutdown() {
        if (isInitialized()) {
            CefUtil.shutdown();
            client = null;
            app = null;
        }
    }

    private static void assertInitialized() {
        if (!isInitialized()) throw new IllegalStateException("Chromium is not initialized");
    }

    /** Commit of java-cef this build was made from. */
    public static String getJavaCefCommit() {
        return Natives.javaCefCommit();
    }
}
