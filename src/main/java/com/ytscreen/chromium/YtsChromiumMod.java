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

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

/**
 * Client entry point: downloads the natives in the background, starts Chromium on the render thread when they are
 * ready, pumps Chromium's message loop every frame and shows the download progress on menu screens.
 */
@Mod(value = YtsChromiumMod.MODID, dist = Dist.CLIENT)
public final class YtsChromiumMod {
    public static final String MODID = "ytscreen_chromium";

    public YtsChromiumMod(IEventBus modBus, ModContainer container) {
        MCEF.getSettings();
        Natives.start();
        NeoForge.EVENT_BUS.addListener(YtsChromiumMod::onClientTick);
        NeoForge.EVENT_BUS.addListener(YtsChromiumMod::onRenderFrame);
        NeoForge.EVENT_BUS.addListener(YtsChromiumMod::onScreenRender);
        NeoForge.EVENT_BUS.addListener(YtsChromiumMod::onShutdown);
        if (SelfTest.enabled()) NeoForge.EVENT_BUS.addListener(SelfTest::onTick);
    }

    private static void onClientTick(ClientTickEvent.Post e) {
        // start once the natives are there and the game shows its first screen (the window exists by then)
        if (!MCEF.isInitialized() && !MCEF.hasFailed() && Natives.state() == Natives.State.READY) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null || mc.level != null) MCEF.initialize();
        }
    }

    private static void onRenderFrame(RenderFrameEvent.Pre e) {
        if (MCEF.isInitialized()) {
            try {
                MCEF.getApp().getHandle().N_DoMessageLoopWork();
            } catch (Throwable t) {
                MCEF.getLogger().error("Chromium message loop failed", t);
            }
        }
    }

    /** Small status line at the bottom of menu screens while Chromium is being prepared (or failed). */
    private static void onScreenRender(ScreenEvent.Render.Post e) {
        Natives.State s = Natives.state();
        if (MCEF.isInitialized()) return;
        Component line;
        switch (s) {
            case DOWNLOADING -> line = Component.translatable("ytscreen_chromium.status.downloading", Math.round(Natives.progress() * 100));
            case EXTRACTING -> line = Component.translatable("ytscreen_chromium.status.extracting", Math.round(Natives.progress() * 100));
            case VERIFYING -> line = Component.translatable("ytscreen_chromium.status.verifying", Math.round(Natives.progress() * 100));
            case FAILED -> line = Component.translatable("ytscreen_chromium.status.failed", Natives.error());
            case READY -> {
                if (!MCEF.hasFailed()) return;
                line = Component.translatable("ytscreen_chromium.status.start_failed");
            }
            default -> {
                return;
            }
        }
        GuiGraphics g = e.getGuiGraphics();
        Font font = Minecraft.getInstance().font;
        int w = Math.min(font.width(line), g.guiWidth() - 8);
        int x = (g.guiWidth() - w) / 2;
        int y = g.guiHeight() - 14;
        g.fill(x - 4, y - 3, x + w + 4, y + 10, 0xC0000000);
        g.drawString(font, font.plainSubstrByWidth(line.getString(), w), x, y, s == Natives.State.FAILED || MCEF.hasFailed() ? 0xFFFF6060 : 0xFFFFFFFF, false);
        if (s == Natives.State.DOWNLOADING || s == Natives.State.EXTRACTING || s == Natives.State.VERIFYING) {
            g.fill(x - 4, y + 10, x - 4 + (int) ((w + 8) * Natives.progress()), y + 11, 0xFFFF0033);
        }
    }

    private static void onShutdown(GameShuttingDownEvent e) {
        MCEF.shutdown();
        // Chromium's helper processes sometimes outlive the game (seen on Windows). Only this game's own child
        // processes are touched, never the Chromium of another program or another Minecraft instance.
        try {
            java.util.List<ProcessHandle> helpers = ProcessHandle.current().descendants()
                    .filter(p -> p.info().command().map(c -> c.toLowerCase(java.util.Locale.ROOT).contains("jcef")).orElse(false))
                    .toList();
            long deadline = System.currentTimeMillis() + 2000;
            for (ProcessHandle p : helpers) {
                long wait = deadline - System.currentTimeMillis();
                try {
                    if (wait > 0) p.onExit().get(wait, java.util.concurrent.TimeUnit.MILLISECONDS);
                } catch (Exception ignored) {
                }
                if (p.isAlive()) p.destroyForcibly();
            }
        } catch (Exception ex) {
            MCEF.getLogger().debug("Could not check for lingering Chromium processes", ex);
        }
    }
}
