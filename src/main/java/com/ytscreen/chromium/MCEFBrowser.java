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

import com.ytscreen.chromium.listeners.MCEFCursorChangeListener;
import net.minecraft.client.Minecraft;
import org.cef.CefBrowserSettings;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefBrowserOsr;
import org.cef.browser.CefFrame;
import org.cef.callback.CefDragData;
import org.cef.event.CefKeyEvent;
import org.cef.event.CefMouseEvent;
import org.cef.event.CefMouseWheelEvent;
import org.cef.handler.CefScreenInfo;
import org.cef.misc.CefCursorType;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryUtil;

import java.awt.Point;
import java.awt.Rectangle;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.lwjgl.glfw.GLFW.*;

/**
 * An off-screen rendered Chromium browser: paints into an OpenGL texture ({@link #getRenderer()}) and takes
 * Minecraft's (GLFW) mouse and keyboard input. Optional browser shortcuts: Ctrl+R reload, Ctrl +/-/0 zoom,
 * Alt+Left/Right history, and the editing commands (select all, copy, cut, paste, undo, redo) with Ctrl (Cmd on macOS)
 * that work with any keyboard layout. Coordinates are in browser pixels.
 * <p>
 * Everything here runs on the render thread.
 */
public class MCEFBrowser extends CefBrowserOsr {
    private static final int DOUBLE_CLICK_MS = 500;
    private static final int DOUBLE_CLICK_PX = 4;

    private final MCEFRenderer renderer;
    private final MCEFDragContext dragContext = new MCEFDragContext();
    private MCEFCursorChangeListener cursorChangeListener;
    private boolean browserControls = true;
    private boolean popupsInPlace = true;
    private boolean autoDSF;
    private double deviceScaleFactor = 1;
    private boolean closed;

    /** Size of the last full frame; a different size means the texture must be re-created. */
    private int frameWidth, frameHeight;
    /** Pressed mouse buttons as java-cef modifier flags. */
    private int btnMask;
    /** Keys whose press was used as a shortcut: their release is not sent to the page either. */
    private final Set<Integer> swallowedKeys = new HashSet<>();

    // multi-click detection
    private long lastClickTime;
    private int lastClickButton = -1, lastClickX, lastClickY, clickCount = 1;

    // popup widgets (<select> lists): Chromium paints them separately and they are drawn over the view
    private Rectangle popupRect;
    private ByteBuffer popupPixels;
    private boolean popupVisible;

    public MCEFBrowser(MCEFClient client, String url, boolean transparent) {
        this(client, url, transparent, false);
    }

    public MCEFBrowser(MCEFClient client, String url, boolean transparent, boolean autoDSF) {
        this(client, url, transparent, autoDSF, new CefBrowserSettings());
    }

    /**
     * @param settings per-browser settings, e.g. {@code windowless_frame_rate} (0 = Chromium's default of 30 fps)
     */
    public MCEFBrowser(MCEFClient client, String url, boolean transparent, boolean autoDSF, CefBrowserSettings settings) {
        super(client.getHandle(), url, transparent, null, settings);
        renderer = new MCEFRenderer(transparent);
        cursorChangeListener = cefCursorID -> setCursor(CefCursorType.fromId(cefCursorID));
        this.autoDSF = autoDSF;
    }

    public MCEFRenderer getRenderer() {
        return renderer;
    }

    // ------------------------------------------------------------------ device scale factor

    public void setAutoDSF(boolean autoDSF) {
        this.autoDSF = autoDSF;
    }

    public boolean isAutoDSF() {
        return autoDSF;
    }

    public void setDeviceScaleFactor(double deviceScaleFactor) {
        this.deviceScaleFactor = deviceScaleFactor;
    }

    /** Ratio of framebuffer to window size when automatic (HiDPI screens), otherwise the value that was set. */
    public double getDeviceScaleFactor() {
        if (!autoDSF) return deviceScaleFactor > 0 ? deviceScaleFactor : 1;
        long window = Minecraft.getInstance().getWindow().getWindow();
        int[] fbW = new int[1], fbH = new int[1], winW = new int[1], winH = new int[1];
        GLFW.glfwGetFramebufferSize(window, fbW, fbH);
        GLFW.glfwGetWindowSize(window, winW, winH);
        if (winW[0] <= 0 || winH[0] <= 0) return 1;
        return Math.max(1, Math.min(fbW[0] / (double) winW[0], fbH[0] / (double) winH[0]));
    }

    private int scale(int v) {
        return (int) (v / getDeviceScaleFactor());
    }

    @Override
    public boolean getScreenInfo(CefBrowser browser, CefScreenInfo screenInfo) {
        super.getScreenInfo(browser, screenInfo);
        screenInfo.device_scale_factor = getDeviceScaleFactor();
        return true;
    }

    public MCEFCursorChangeListener getCursorChangeListener() {
        return cursorChangeListener;
    }

    public void setCursorChangeListener(MCEFCursorChangeListener cursorChangeListener) {
        this.cursorChangeListener = cursorChangeListener;
    }

    public boolean usingBrowserControls() {
        return browserControls;
    }

    /** Browser shortcuts (reload, zoom, history, editing commands); on by default. */
    public MCEFBrowser useBrowserControls(boolean browserControls) {
        this.browserControls = browserControls;
        return this;
    }

    /** Links that open a new window open in this browser instead (on by default); see {@link MCEFClient}. */
    public MCEFBrowser setOpenPopupsInPlace(boolean inPlace) {
        this.popupsInPlace = inPlace;
        return this;
    }

    public boolean opensPopupsInPlace() {
        return popupsInPlace;
    }

    public MCEFDragContext getDragContext() {
        return dragContext;
    }

    // ------------------------------------------------------------------ painting

    @Override
    public void onPopupShow(CefBrowser browser, boolean show) {
        super.onPopupShow(browser, show);
        popupVisible = show;
        if (!show) {
            popupRect = null;
            freePopupPixels();
            invalidate(); // repaint the view where the popup was
        }
    }

    private void freePopupPixels() {
        if (popupPixels != null) {
            MemoryUtil.memFree(popupPixels);
            popupPixels = null;
        }
    }

    @Override
    public void onPopupSize(CefBrowser browser, Rectangle size) {
        super.onPopupSize(browser, size);
        popupRect = new Rectangle(size);
        freePopupPixels();
    }

    @Override
    public void onPaint(CefBrowser browser, boolean popup, Rectangle[] dirtyRects, ByteBuffer buffer, int width, int height) {
        if (closed || dirtyRects.length == 0) return;
        if (!popup) {
            if (width != frameWidth || height != frameHeight || renderer.getTextureID() == 0) {
                frameWidth = width;
                frameHeight = height;
                renderer.uploadFull(buffer, width, height);
            } else {
                for (Rectangle r : dirtyRects) renderer.uploadRect(buffer, width, r.x, r.y, r.x, r.y, r.width, r.height);
            }
            // the view was repainted below an open popup: draw the popup over it again
            if (popupVisible && popupPixels != null) uploadPopup();
        } else if (popupRect != null && width == popupRect.width && height == popupRect.height) {
            // Chromium only lends the buffer for this call: keep a copy to draw the popup again after view repaints
            int bytes = width * height * 4;
            if (popupPixels != null && popupPixels.capacity() != bytes) freePopupPixels();
            if (popupPixels == null) popupPixels = MemoryUtil.memAlloc(bytes);
            MemoryUtil.memCopy(MemoryUtil.memAddress(buffer), MemoryUtil.memAddress(popupPixels), bytes);
            if (popupVisible) uploadPopup();
        }
    }

    /** Draws the stored popup at its position, clipped to the view. */
    private void uploadPopup() {
        Rectangle r = popupRect;
        if (r == null || renderer.getTextureID() == 0) return;
        int x0 = Math.max(0, r.x), y0 = Math.max(0, r.y);
        int x1 = Math.min(frameWidth, r.x + r.width), y1 = Math.min(frameHeight, r.y + r.height);
        renderer.uploadRect(popupPixels, r.width, x0 - r.x, y0 - r.y, x0, y0, x1 - x0, y1 - y0);
    }

    /** Asks Chromium to repaint the whole view (the next paint event has the full frame). */
    public void requestRepaint() {
        invalidate();
    }

    public void resize(int width, int height) {
        width = Math.max(1, scale(width));
        height = Math.max(1, scale(height));
        browser_rect_.setBounds(0, 0, width, height);
        wasResized(width, height);
    }

    // ------------------------------------------------------------------ keyboard

    /** Ctrl, or Cmd on macOS. */
    private static int primaryModifier() {
        return MCEFPlatform.getPlatform().isMacOS() ? GLFW_MOD_SUPER : GLFW_MOD_CONTROL;
    }

    /** Runs a browser shortcut for this key press; true if the key was used for one. */
    private boolean shortcut(int keyCode, int modifiers) {
        if (!browserControls) return false;
        int mods = modifiers & (GLFW_MOD_SHIFT | GLFW_MOD_CONTROL | GLFW_MOD_ALT | GLFW_MOD_SUPER);
        int primary = primaryModifier();
        if (mods == primary || mods == (primary | GLFW_MOD_SHIFT)) {
            boolean shift = (mods & GLFW_MOD_SHIFT) != 0;
            CefFrame frame = getFocusedFrame();
            switch (keyCode) {
                case GLFW_KEY_A, GLFW_KEY_C, GLFW_KEY_X, GLFW_KEY_V, GLFW_KEY_Z, GLFW_KEY_Y -> {
                    // Editing through the frame works with any keyboard layout. The key events alone do not: the
                    // natives derive the key from its US-layout character, which other layouts (e.g. Russian) lack.
                    if (frame == null) return false;
                    switch (keyCode) {
                        case GLFW_KEY_A -> frame.selectAll();
                        case GLFW_KEY_C -> frame.copy();
                        case GLFW_KEY_X -> frame.cut();
                        case GLFW_KEY_V -> frame.paste();
                        case GLFW_KEY_Z -> {
                            if (shift) frame.redo();
                            else frame.undo();
                        }
                        default -> frame.redo();
                    }
                    return true;
                }
                default -> {
                }
            }
            if (shift) return false;
            switch (keyCode) {
                case GLFW_KEY_R -> reload();
                case GLFW_KEY_EQUAL, GLFW_KEY_KP_ADD -> setZoomLevel(Math.min(9, getZoomLevel() + 1));
                case GLFW_KEY_MINUS, GLFW_KEY_KP_SUBTRACT -> setZoomLevel(Math.max(-9, getZoomLevel() - 1));
                case GLFW_KEY_0, GLFW_KEY_KP_0 -> setZoomLevel(0);
                default -> {
                    return false;
                }
            }
            return true;
        }
        if (mods == GLFW_MOD_ALT) {
            if (keyCode == GLFW_KEY_LEFT && canGoBack()) {
                goBack();
                return true;
            }
            if (keyCode == GLFW_KEY_RIGHT && canGoForward()) {
                goForward();
                return true;
            }
        }
        return false;
    }

    /** @param keyCode GLFW key, @param modifiers GLFW modifier bits */
    public void sendKeyPress(int keyCode, long scanCode, int modifiers) {
        if (shortcut(keyCode, modifiers)) {
            swallowedKeys.add(keyCode);
            return;
        }
        sendKeyEvent(keyEvent(CefKeyEvent.KEY_PRESS, keyCode, modifiers));
    }

    public void sendKeyRelease(int keyCode, long scanCode, int modifiers) {
        if (swallowedKeys.remove(keyCode)) return;
        sendKeyEvent(keyEvent(CefKeyEvent.KEY_RELEASE, keyCode, modifiers));
    }

    /** A typed character (text input). Characters typed together with Ctrl/Cmd are shortcuts, not text. */
    public void sendKeyTyped(char c, int modifiers) {
        if (c == 0) return;
        sendKeyEvent(new CefKeyEvent(CefKeyEvent.KEY_TYPE, c, c, SdlInput.modifiers(modifiers)));
    }

    /** The natives identify keys by SDL keycode and scancode (see {@link SdlInput}). */
    private static CefKeyEvent keyEvent(int type, int glfwKey, int modifiers) {
        int sdlKey = SdlInput.keycode(glfwKey);
        CefKeyEvent e = new CefKeyEvent(type, sdlKey, SdlInput.keyChar(sdlKey), SdlInput.modifiers(modifiers));
        e.scancode = SdlInput.scancode(glfwKey);
        return e;
    }

    // ------------------------------------------------------------------ mouse

    public void sendMouseMove(int mouseX, int mouseY) {
        mouseX = scale(mouseX);
        mouseY = scale(mouseY);
        sendMouseEvent(new CefMouseEvent(CefMouseEvent.MOUSE_MOVED, mouseX, mouseY, 0, 0, dragContext.getVirtualModifiers(btnMask)));
        if (dragContext.isDragging()) dragTargetDragOver(new Point(mouseX, mouseY), 0, dragContext.getMask());
    }

    /** @param button GLFW mouse button (0 left, 1 right, 2 middle) */
    public void sendMousePress(int mouseX, int mouseY, int button) {
        mouseX = scale(mouseX);
        mouseY = scale(mouseY);
        int sdlButton = SdlInput.button(button);
        if (sdlButton < 0) return;
        long now = System.currentTimeMillis();
        boolean repeat = button == lastClickButton && now - lastClickTime < DOUBLE_CLICK_MS
                && Math.abs(mouseX - lastClickX) <= DOUBLE_CLICK_PX && Math.abs(mouseY - lastClickY) <= DOUBLE_CLICK_PX;
        clickCount = repeat ? Math.min(3, clickCount + 1) : 1; // 2 selects a word, 3 a paragraph
        lastClickTime = now;
        lastClickButton = button;
        lastClickX = mouseX;
        lastClickY = mouseY;
        btnMask |= SdlInput.buttonMask(button);
        sendMouseEvent(new CefMouseEvent(CefMouseEvent.MOUSE_PRESSED, mouseX, mouseY, clickCount, sdlButton, btnMask));
    }

    /** @param button GLFW mouse button (0 left, 1 right, 2 middle) */
    public void sendMouseRelease(int mouseX, int mouseY, int button) {
        mouseX = scale(mouseX);
        mouseY = scale(mouseY);
        int sdlButton = SdlInput.button(button);
        if (sdlButton < 0) return;
        btnMask &= ~SdlInput.buttonMask(button);
        sendMouseEvent(new CefMouseEvent(CefMouseEvent.MOUSE_RELEASED, mouseX, mouseY, clickCount, sdlButton, btnMask));
        if (dragContext.isDragging() && button == GLFW_MOUSE_BUTTON_LEFT) drop(mouseX, mouseY);
    }

    /**
     * @param amount    wheel notches as Minecraft reports them (fractions from touchpads); positive scrolls up
     * @param modifiers GLFW modifier bits: Shift scrolls sideways, Ctrl zooms (browser controls)
     */
    public void sendMouseWheel(int mouseX, int mouseY, double amount, int modifiers) {
        mouseX = scale(mouseX);
        mouseY = scale(mouseY);
        if (browserControls && (modifiers & GLFW_MOD_CONTROL) != 0) {
            if (amount > 0) setZoomLevel(Math.min(9, getZoomLevel() + 1));
            else if (amount < 0) setZoomLevel(Math.max(-9, getZoomLevel() - 1));
            return;
        }
        // 3 lines per notch like a desktop browser; macOS already reports scaled (and smooth) deltas
        if (!MCEFPlatform.getPlatform().isMacOS()) amount *= 3;
        sendMouseWheelEvent(new CefMouseWheelEvent(CefMouseWheelEvent.WHEEL_UNIT_SCROLL, mouseX, mouseY, amount, SdlInput.modifiers(modifiers)));
    }

    // ------------------------------------------------------------------ drag & drop inside the page

    @Override
    public boolean startDragging(CefBrowser browser, CefDragData dragData, int mask, int x, int y) {
        // x, y are view coordinates already
        dragContext.startDragging(dragData, mask);
        dragTargetDragEnter(dragContext.getDragData(), new Point(x, y), btnMask, dragContext.getMask());
        // false: CEF does not start a native (OS) drag, which cannot work off screen; the page sees it as an internal drag
        return false;
    }

    @Override
    public void updateDragCursor(CefBrowser browser, int operation) {
        if (dragContext.updateCursor(operation)) onCursorChange(this, dragContext.getVirtualCursor(dragContext.getActualCursor()));
        super.updateDragCursor(browser, operation);
    }

    /** @param x view coordinates */
    private void drop(int x, int y) {
        dragTargetDrop(new Point(x, y), btnMask);
        dragTargetDragLeave();
        dragContext.stopDragging();
        onCursorChange(this, dragContext.getActualCursor());
    }

    public void cancelDrag() {
        if (!dragContext.isDragging()) return;
        dragTargetDragLeave();
        dragContext.stopDragging();
        onCursorChange(this, dragContext.getActualCursor());
    }

    // ------------------------------------------------------------------ closing

    public boolean isClosed() {
        return closed;
    }

    /** Closes the browser and frees its texture. Safe to call more than once. */
    public void close() {
        if (closed) return;
        closed = true;
        cancelDrag();
        renderer.cleanup();
        freePopupPixels();
        cursorChangeListener.onCursorChange(CefCursorType.POINTER.ordinal());
        super.close(true);
    }

    // ------------------------------------------------------------------ cursor

    @Override
    public boolean onCursorChange(CefBrowser browser, int cursorType) {
        cursorType = dragContext.getVirtualCursor(cursorType);
        cursorChangeListener.onCursorChange(cursorType);
        return super.onCursorChange(browser, cursorType);
    }

    /** Shows the cursor the page asks for (the default cursor listener). */
    public void setCursor(CefCursorType cursorType) {
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (cursorType == CefCursorType.NONE) {
            GLFW.glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_HIDDEN);
        } else {
            GLFW.glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
            GLFW.glfwSetCursor(window, glfwCursor(cursorType));
        }
    }

    // GLFW standard cursors for CEF cursor types (created once, owned by GLFW until the window is destroyed)
    private static final Map<Integer, Long> GLFW_CURSORS = new HashMap<>();

    private static long glfwCursor(CefCursorType type) {
        int shape = switch (type) {
            case HAND -> GLFW_HAND_CURSOR;
            case IBEAM, VERTICAL_IBEAM -> GLFW_IBEAM_CURSOR;
            case CROSS, CELL -> GLFW_CROSSHAIR_CURSOR;
            case EAST_RESIZE, WEST_RESIZE, EAST_WEST_RESIZE, COLUMN_RESIZE -> GLFW_HRESIZE_CURSOR;
            case NORTH_RESIZE, SOUTH_RESIZE, NORTH_SOUTH_RESIZE, ROW_RESIZE -> GLFW_VRESIZE_CURSOR;
            default -> GLFW_ARROW_CURSOR;
        };
        return GLFW_CURSORS.computeIfAbsent(shape, GLFW::glfwCreateStandardCursor);
    }
}
