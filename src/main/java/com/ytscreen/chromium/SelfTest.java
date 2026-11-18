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

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Automated check used by CI (-Dytscreen.chromium.selftest=true): installs the natives, starts Chromium, renders a
 * page with JavaScript, checks the console bridge and the texture, mouse and keyboard input, the editing shortcuts,
 * popups (opened in the same browser) and address reports, writes chromium-selftest.txt/png and exits.
 */
final class SelfTest {
    private static final long TIMEOUT_MS = 300_000;
    private static final String PAGE = "data:text/html;charset=utf-8,"
            + java.net.URLEncoder.encode("<html><body style='margin:0;background:#c00'>"
            + "<div id=d style='position:absolute;left:0;top:0;width:50%;height:100%;background:#0c0' onclick=\"console.log('SELFTEST-CLICK')\"></div>"
            + "<input id=i style='position:absolute;left:60%;top:10px;width:200px;height:30px' oninput=\"console.log('SELFTEST-INPUT:'+this.value)\">"
            + "<a id=l target=_blank href='https://example.org/' style='position:absolute;left:60%;top:220px;width:200px;height:60px;display:block;background:#00c'>popup</a>"
            + "<script>console.log('SELFTEST:' + navigator.userAgent + ' reduced-motion=' + matchMedia('(prefers-reduced-motion: reduce)').matches);</script></body></html>", StandardCharsets.UTF_8).replace("+", "%20");
    private static final long started = System.currentTimeMillis();
    private static final List<String> log = new ArrayList<>();
    private static MCEFBrowser browser;
    private static MCEFBrowser second;
    private static boolean secondLoaded;
    private static long browserAt;
    private static volatile String consoleLine;
    private static volatile String address = "";
    private static final java.util.Set<String> console = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean done;
    private static int phase;
    private static long phaseAt;

    private SelfTest() {
    }

    static boolean enabled() {
        return Boolean.getBoolean("ytscreen.chromium.selftest");
    }

    private static void note(String s) {
        String line = String.format("[%6.1fs] %s", (System.currentTimeMillis() - started) / 1000.0, s);
        log.add(line);
        MCEF.getLogger().info("[selftest] {}", line);
    }

    static void onTick(ClientTickEvent.Post e) {
        if (done) return;
        long now = System.currentTimeMillis();
        if (now - started > TIMEOUT_MS) {
            finish("FAIL: timeout, natives state " + Natives.state() + " " + Natives.error());
            return;
        }
        if (Natives.state() == Natives.State.FAILED || MCEF.hasFailed()) {
            finish("FAIL: " + (Natives.error().isEmpty() ? "Chromium did not start" : Natives.error()));
            return;
        }
        if (!MCEF.isInitialized()) return;
        if (browser == null) {
            note("Chromium started, java-cef " + MCEF.getJavaCefCommit());
            MCEF.getClient().addDisplayHandler(new org.cef.handler.CefDisplayHandlerAdapter() {
                @Override
                public boolean onConsoleMessage(org.cef.browser.CefBrowser b, org.cef.CefSettings.LogSeverity level, String message, String source, int line) {
                    if (b == browser && message.startsWith("SELFTEST:")) consoleLine = message.substring(9);
                    else if ((b == browser || b == second) && message.startsWith("SELFTEST-")) console.add(message);
                    return false;
                }

                @Override
                public void onAddressChange(org.cef.browser.CefBrowser b, org.cef.browser.CefFrame frame, String url) {
                    if (b == browser && (frame == null || frame.isMain())) address = url;
                }
            });
            browser = MCEF.createBrowser(PAGE, false);
            browser.resize(640, 360);
            browserAt = now;
            return;
        }
        if (phase == 1) {
            // input: a click on the green area must reach the page's onclick handler
            if (console.contains("SELFTEST-CLICK")) {
                note("mouse click reached the page");
                browser.sendMousePress(480, 25, 0);
                browser.sendMouseRelease(480, 25, 0);
                browser.setFocus(true);
                phase = 2;
                phaseAt = now;
            } else if (now - phaseAt > 8000) finish("FAIL: mouse clicks do not reach the page");
            return;
        }
        if (phase == 2) {
            if (now - phaseAt < 800) return;
            if (now - phaseAt < 1000) {
                type("ab");
                phaseAt -= 200; // type once
                return;
            }
            if (console.contains("SELFTEST-INPUT:ab")) {
                note("keyboard input reached the page");
                // Ctrl+A (editing command through the frame, independent of the keyboard layout), then type over it
                key(org.lwjgl.glfw.GLFW.GLFW_KEY_A, org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL);
                type("c");
                phase = 3;
                phaseAt = now;
            } else if (now - phaseAt > 8000) finish("FAIL: typed text does not reach the page " + console);
            return;
        }
        if (phase == 3) {
            if (console.contains("SELFTEST-INPUT:c")) {
                note("Ctrl+A selected the text");
                key(org.lwjgl.glfw.GLFW.GLFW_KEY_A, org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL);
                key(org.lwjgl.glfw.GLFW.GLFW_KEY_C, org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL);
                key(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT, 0);
                key(org.lwjgl.glfw.GLFW.GLFW_KEY_V, org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL);
                phase = 4;
                phaseAt = now;
            } else if (now - phaseAt > 8000) finish("FAIL: Ctrl+A did not select the text " + console);
            return;
        }
        if (phase == 4) {
            boolean pasted = console.contains("SELFTEST-INPUT:cc");
            if (!pasted && now - phaseAt < 5000) return;
            // the clipboard depends on the desktop (none on a bare virtual display): reported, not required
            note(pasted ? "Ctrl+C / Ctrl+V copied and pasted" : "clipboard not available here (Ctrl+C / Ctrl+V not checked)");
            browser.sendMouseMove(480, 250);
            browser.sendMousePress(480, 250, 0);
            browser.sendMouseRelease(480, 250, 0);
            phase = 5;
            phaseAt = now;
            return;
        }
        if (phase == 5) {
            // target=_blank must open in the same browser, not as a native window
            if (address.startsWith("https://example.org")) {
                note("link with target=_blank opened in the same browser: " + address);
                browser.executeJavaScript("history.pushState({}, '', '/yts-pushstate')", address, 0);
                phase = 6;
                phaseAt = now;
            } else if (now - phaseAt > 20_000) finish("FAIL: popup link did not open in the browser (address " + address + ")");
            return;
        }
        if (phase == 6) {
            // single-page sites change the address with the history API: the display handler must report it
            if (address.endsWith("/yts-pushstate")) {
                note("history.pushState reported: " + address);
                // a second browser with its own user agent
                second = MCEF.createBrowser("about:blank", false);
                second.setUserAgentOverride("YTS-SelfTest-Agent/1");
                second.resize(320, 180);
                phase = 7;
                phaseAt = now;
            } else if (now - phaseAt > 10_000) finish("FAIL: history.pushState not reported (address " + address + ")");
            return;
        }
        if (phase == 7) {
            if (now - phaseAt > 3000 && !secondLoaded) {
                secondLoaded = true;
                second.loadURL("data:text/html,<script>console.log('SELFTEST-UA:'%2Bnavigator.userAgent)</script>");
            }
            if (console.contains("SELFTEST-UA:YTS-SelfTest-Agent/1")) {
                note("per-browser user agent applied");
                finish("PASS");
            } else if (now - phaseAt > 15_000) finish("FAIL: per-browser user agent not applied " + console);
            return;
        }
        if (now - browserAt < 4000 || consoleLine == null || browser.getRenderer().getTextureID() == 0) {
            if (now - browserAt > 60_000) finish("FAIL: page not rendered (console=" + consoleLine + ", texture=" + browser.getRenderer().getTextureID() + ")");
            return;
        }
        note("user agent: " + consoleLine);
        // sites must see a normal Chrome (an unknown browser gets fallback pages) and the reduced-motion preference
        if (!consoleLine.contains("Chrome/") || !consoleLine.endsWith("reduced-motion=true")) {
            finish("FAIL: unexpected user agent or motion preference");
            return;
        }
        // left half green, right half red
        int tex = browser.getRenderer().getTextureID();
        int w = 640, h = 360;
        ByteBuffer buf = BufferUtils.createByteBuffer(w * h * 4);
        RenderSystem.bindTexture(tex);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        int[] tw = new int[1], th = new int[1];
        GL11.glGetTexLevelParameteriv(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH, tw);
        GL11.glGetTexLevelParameteriv(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT, th);
        if (tw[0] != w || th[0] != h) {
            RenderSystem.bindTexture(0);
            finish("FAIL: texture size " + tw[0] + "x" + th[0]);
            return;
        }
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, buf);
        RenderSystem.bindTexture(0);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = (y * w + x) * 4;
                img.setRGB(x, y, ((buf.get(i + 2) & 0xFF) << 16) | ((buf.get(i + 1) & 0xFF) << 8) | (buf.get(i) & 0xFF));
            }
        }
        try {
            ImageIO.write(img, "png", new File(Minecraft.getInstance().gameDirectory, "chromium-selftest.png"));
        } catch (Exception ex) {
            note("could not save png: " + ex);
        }
        int left = img.getRGB(w / 4, h / 2) & 0xFFFFFF, right = img.getRGB(3 * w / 4, h / 2) & 0xFFFFFF;
        note(String.format("pixels: left=%06x right=%06x", left, right));
        boolean greenLeft = ((left >> 8) & 0xFF) > 150 && ((left >> 16) & 0xFF) < 80;
        boolean redRight = ((right >> 16) & 0xFF) > 150 && ((right >> 8) & 0xFF) < 80;
        if (!(greenLeft && redRight)) {
            finish("FAIL: wrong picture");
            return;
        }
        note("picture ok");
        browser.sendMouseMove(160, 180);
        browser.sendMousePress(160, 180, 0);
        browser.sendMouseRelease(160, 180, 0);
        phase = 1;
        phaseAt = now;
    }

    private static void type(String text) {
        for (char ch : text.toCharArray()) {
            int key = org.lwjgl.glfw.GLFW.GLFW_KEY_A + (ch - 'a');
            browser.sendKeyPress(key, 0, 0);
            browser.sendKeyTyped(ch, 0);
            browser.sendKeyRelease(key, 0, 0);
        }
    }

    private static void key(int key, int mods) {
        browser.sendKeyPress(key, 0, mods);
        browser.sendKeyRelease(key, 0, mods);
    }

    private static void finish(String result) {
        done = true;
        note("RESULT: " + result);
        try {
            Files.writeString(new File(Minecraft.getInstance().gameDirectory, "chromium-selftest.txt").toPath(), String.join("\n", log) + "\n");
        } catch (Exception ex) {
            MCEF.getLogger().error("could not write the self-test result", ex);
        }
        if (browser != null) browser.close();
        if (second != null) second.close();
        Minecraft.getInstance().stop();
    }
}
