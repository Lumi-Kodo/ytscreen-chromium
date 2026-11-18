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

import org.cef.CefClient;
import org.cef.CefSettings;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefAuthCallback;
import org.cef.callback.CefCallback;
import org.cef.callback.CefContextMenuParams;
import org.cef.callback.CefMenuModel;
import org.cef.handler.CefContextMenuHandler;
import org.cef.handler.CefDisplayHandler;
import org.cef.handler.CefLifeSpanHandler;
import org.cef.handler.CefLoadHandler;
import org.cef.handler.CefRequestHandler;
import org.cef.handler.CefResourceRequestHandler;
import org.cef.misc.BoolRef;
import org.cef.network.CefRequest;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A wrapper around {@link CefClient}. CEF keeps only one handler of each kind per client, so this class is that
 * handler and forwards every event to all handlers that mods registered here (load, display, context menu, request and
 * popup events). Register through this class, never directly on {@link #getHandle()}.
 * <p>
 * Popups ({@code target="_blank"} links, {@code window.open}) would open as separate native windows, which an
 * off-screen browser inside a game cannot show. Unless a registered {@link PopupHandler} handles them, they are opened
 * in the browser that asked for them instead.
 * <p>
 * Unlike MCEF, no audio handler is registered: Chromium plays the sound itself (MCEF's audio capture gave mono
 * sound and leaked native memory).
 */
public class MCEFClient implements CefLoadHandler, CefContextMenuHandler, CefDisplayHandler, CefRequestHandler, CefLifeSpanHandler {
    /** Decides what happens with a popup. Return true if it was handled (the popup is then not opened). */
    @FunctionalInterface
    public interface PopupHandler {
        boolean onPopup(CefBrowser browser, CefFrame frame, String targetUrl);
    }

    private final CefClient handle;
    private final List<CefLoadHandler> loadHandlers = new CopyOnWriteArrayList<>();
    private final List<CefContextMenuHandler> contextMenuHandlers = new CopyOnWriteArrayList<>();
    private final List<CefDisplayHandler> displayHandlers = new CopyOnWriteArrayList<>();
    private final List<CefRequestHandler> requestHandlers = new CopyOnWriteArrayList<>();
    private final List<PopupHandler> popupHandlers = new CopyOnWriteArrayList<>();

    public MCEFClient(CefClient cefClient) {
        handle = cefClient;
        cefClient.addLoadHandler(this);
        cefClient.addContextMenuHandler(this);
        cefClient.addDisplayHandler(this);
        cefClient.addRequestHandler(this);
        cefClient.addLifeSpanHandler(this);
    }

    public CefClient getHandle() {
        return handle;
    }

    // ------------------------------------------------------------------ load

    public void addLoadHandler(CefLoadHandler handler) {
        loadHandlers.add(handler);
    }

    public void removeLoadHandler(CefLoadHandler handler) {
        loadHandlers.remove(handler);
    }

    @Override
    public void onLoadingStateChange(CefBrowser browser, boolean isLoading, boolean canGoBack, boolean canGoForward) {
        for (CefLoadHandler h : loadHandlers) h.onLoadingStateChange(browser, isLoading, canGoBack, canGoForward);
    }

    @Override
    public void onLoadStart(CefBrowser browser, CefFrame frame, CefRequest.TransitionType transitionType) {
        for (CefLoadHandler h : loadHandlers) h.onLoadStart(browser, frame, transitionType);
    }

    @Override
    public void onLoadEnd(CefBrowser browser, CefFrame frame, int httpStatusCode) {
        for (CefLoadHandler h : loadHandlers) h.onLoadEnd(browser, frame, httpStatusCode);
    }

    @Override
    public void onLoadError(CefBrowser browser, CefFrame frame, ErrorCode errorCode, String errorText, String failedUrl) {
        for (CefLoadHandler h : loadHandlers) h.onLoadError(browser, frame, errorCode, errorText, failedUrl);
    }

    // ------------------------------------------------------------------ context menu

    public void addContextMenuHandler(CefContextMenuHandler handler) {
        contextMenuHandlers.add(handler);
    }

    public void removeContextMenuHandler(CefContextMenuHandler handler) {
        contextMenuHandlers.remove(handler);
    }

    @Override
    public void onBeforeContextMenu(CefBrowser browser, CefFrame frame, CefContextMenuParams params, CefMenuModel model) {
        for (CefContextMenuHandler h : contextMenuHandlers) h.onBeforeContextMenu(browser, frame, params, model);
    }

    @Override
    public boolean onContextMenuCommand(CefBrowser browser, CefFrame frame, CefContextMenuParams params, int commandId, int eventFlags) {
        for (CefContextMenuHandler h : contextMenuHandlers) {
            if (h.onContextMenuCommand(browser, frame, params, commandId, eventFlags)) return true;
        }
        return false;
    }

    @Override
    public void onContextMenuDismissed(CefBrowser browser, CefFrame frame) {
        for (CefContextMenuHandler h : contextMenuHandlers) h.onContextMenuDismissed(browser, frame);
    }

    // ------------------------------------------------------------------ display

    public void addDisplayHandler(CefDisplayHandler handler) {
        displayHandlers.add(handler);
    }

    public void removeDisplayHandler(CefDisplayHandler handler) {
        displayHandlers.remove(handler);
    }

    @Override
    public void onAddressChange(CefBrowser browser, CefFrame frame, String url) {
        for (CefDisplayHandler h : displayHandlers) h.onAddressChange(browser, frame, url);
    }

    @Override
    public void onTitleChange(CefBrowser browser, String title) {
        for (CefDisplayHandler h : displayHandlers) h.onTitleChange(browser, title);
    }

    @Override
    public void onFullscreenModeChange(CefBrowser browser, boolean fullscreen) {
        for (CefDisplayHandler h : displayHandlers) h.onFullscreenModeChange(browser, fullscreen);
    }

    @Override
    public boolean onTooltip(CefBrowser browser, String text) {
        for (CefDisplayHandler h : displayHandlers) if (h.onTooltip(browser, text)) return true;
        return false;
    }

    @Override
    public void onStatusMessage(CefBrowser browser, String value) {
        for (CefDisplayHandler h : displayHandlers) h.onStatusMessage(browser, value);
    }

    @Override
    public boolean onConsoleMessage(CefBrowser browser, CefSettings.LogSeverity level, String message, String source, int line) {
        for (CefDisplayHandler h : displayHandlers) if (h.onConsoleMessage(browser, level, message, source, line)) return true;
        return false;
    }

    @Override
    public boolean onCursorChange(CefBrowser browser, int cursorType) {
        for (CefDisplayHandler h : displayHandlers) if (h.onCursorChange(browser, cursorType)) return true;
        return false;
    }

    // ------------------------------------------------------------------ requests (called on Chromium's UI and IO threads)

    public void addRequestHandler(CefRequestHandler handler) {
        requestHandlers.add(handler);
    }

    public void removeRequestHandler(CefRequestHandler handler) {
        requestHandlers.remove(handler);
    }

    /** Cancelled if any handler cancels it. */
    @Override
    public boolean onBeforeBrowse(CefBrowser browser, CefFrame frame, CefRequest request, boolean userGesture, boolean isRedirect) {
        boolean cancel = false;
        for (CefRequestHandler h : requestHandlers) cancel |= h.onBeforeBrowse(browser, frame, request, userGesture, isRedirect);
        return cancel;
    }

    @Override
    public boolean onOpenURLFromTab(CefBrowser browser, CefFrame frame, String targetUrl, boolean userGesture) {
        boolean cancel = false;
        for (CefRequestHandler h : requestHandlers) cancel |= h.onOpenURLFromTab(browser, frame, targetUrl, userGesture);
        return cancel;
    }

    /** The first handler that returns one handles the request. */
    @Override
    public CefResourceRequestHandler getResourceRequestHandler(CefBrowser browser, CefFrame frame, CefRequest request, boolean isNavigation,
                                                               boolean isDownload, String requestInitiator, BoolRef disableDefaultHandling) {
        for (CefRequestHandler h : requestHandlers) {
            CefResourceRequestHandler r = h.getResourceRequestHandler(browser, frame, request, isNavigation, isDownload, requestInitiator, disableDefaultHandling);
            if (r != null) return r;
        }
        return null;
    }

    @Override
    public boolean getAuthCredentials(CefBrowser browser, String originUrl, boolean isProxy, String host, int port, String realm, String scheme,
                                      CefAuthCallback callback) {
        for (CefRequestHandler h : requestHandlers) if (h.getAuthCredentials(browser, originUrl, isProxy, host, port, realm, scheme, callback)) return true;
        return false;
    }

    @Override
    public boolean onCertificateError(CefBrowser browser, ErrorCode certError, String requestUrl, CefCallback callback) {
        for (CefRequestHandler h : requestHandlers) if (h.onCertificateError(browser, certError, requestUrl, callback)) return true;
        return false;
    }

    @Override
    public void onRenderProcessTerminated(CefBrowser browser, TerminationStatus status, int errorCode, String errorString) {
        for (CefRequestHandler h : requestHandlers) h.onRenderProcessTerminated(browser, status, errorCode, errorString);
    }

    // ------------------------------------------------------------------ popups and browser life span (UI thread)

    public void addPopupHandler(PopupHandler handler) {
        popupHandlers.add(handler);
    }

    public void removePopupHandler(PopupHandler handler) {
        popupHandlers.remove(handler);
    }

    @Override
    public boolean onBeforePopup(CefBrowser browser, CefFrame frame, String targetUrl, String targetFrameName) {
        for (PopupHandler h : popupHandlers) if (h.onPopup(browser, frame, targetUrl)) return true;
        // no native popup windows: open the address in the same browser
        if (browser != null && targetUrl != null && (targetUrl.startsWith("https://") || targetUrl.startsWith("http://"))) browser.loadURL(targetUrl);
        return true;
    }

    @Override
    public void onAfterCreated(CefBrowser browser) {
    }

    @Override
    public void onAfterParentChanged(CefBrowser browser) {
    }

    @Override
    public boolean doClose(CefBrowser browser) {
        return browser.doClose(); // CefClient's default when no life span handler is set
    }

    @Override
    public void onBeforeClose(CefBrowser browser) {
    }
}
