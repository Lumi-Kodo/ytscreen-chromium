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

import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Settings in config/ytscreen-chromium.properties. Everything has a sensible default; the file is created on the
 * first start so players can find it.
 */
public final class MCEFSettings {
    private static final String DEFAULT_DOWNLOAD_URL = "https://github.com/${repo}/releases/download/natives-${commit}/${platform}.tar.gz";

    private String downloadUrl = DEFAULT_DOWNLOAD_URL;
    private String userAgent = "";
    private final List<String> extraSwitches = new ArrayList<>();
    private boolean useCache = true;

    MCEFSettings() {
    }

    static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("ytscreen-chromium.properties");
    }

    void load() {
        Path f = file();
        Properties p = new Properties();
        if (Files.isRegularFile(f)) {
            try (InputStream in = Files.newInputStream(f)) {
                p.load(in);
            } catch (IOException e) {
                MCEF.getLogger().warn("Could not read {}", f, e);
            }
        }
        downloadUrl = p.getProperty("downloadUrl", DEFAULT_DOWNLOAD_URL).trim();
        if (downloadUrl.isEmpty()) downloadUrl = DEFAULT_DOWNLOAD_URL;
        userAgent = p.getProperty("userAgent", "").trim();
        useCache = !"false".equalsIgnoreCase(p.getProperty("useCache", "true").trim());
        extraSwitches.clear();
        for (String s : p.getProperty("extraSwitches", "").trim().split("\\s+")) {
            if (s.startsWith("--")) extraSwitches.add(s);
        }
        if (!Files.isRegularFile(f)) save();
    }

    private void save() {
        Properties p = new Properties();
        p.setProperty("downloadUrl", downloadUrl);
        p.setProperty("userAgent", userAgent);
        p.setProperty("useCache", String.valueOf(useCache));
        p.setProperty("extraSwitches", String.join(" ", extraSwitches));
        try {
            Files.createDirectories(file().getParent());
            try (OutputStream out = Files.newOutputStream(file())) {
                p.store(out, "YouTube Screen Chromium. downloadUrl: where the Chromium natives come from (${repo}, ${commit}, ${platform}); "
                        + "userAgent: empty = Chromium's own; extraSwitches: additional Chromium command line switches separated by spaces");
            }
        } catch (IOException e) {
            MCEF.getLogger().warn("Could not write {}", file(), e);
        }
    }

    /** Download address template with ${repo}, ${commit} and ${platform}. */
    public String getDownloadUrl() {
        return downloadUrl;
    }

    /** Custom user agent, or empty for Chromium's own. */
    public String getUserAgent() {
        return userAgent;
    }

    public List<String> getExtraSwitches() {
        return extraSwitches;
    }

    /** Keep cookies and site data between game starts (sign-ins). */
    public boolean isUsingCache() {
        return useCache;
    }
}
