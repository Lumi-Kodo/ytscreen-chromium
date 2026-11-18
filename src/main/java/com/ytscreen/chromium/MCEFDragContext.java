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

import org.cef.callback.CefDragData;
import org.cef.misc.CefCursorType;

public class MCEFDragContext {
    private CefDragData dragData = null;
    private int dragMask = 0;
    private int cursorOverride = -1;
    private int actualCursor = -1;

    /**
     * Used to prevent re-selecting stuff while dragging
     * If the user is dragging, emulate having no buttons pressed
     *
     * @param btnMask the actual mask
     * @return a mask modified based on if the user is dragging
     */
    public int getVirtualModifiers(int btnMask) {
        return dragData != null ? 0 : btnMask;
    }

    /**
     * When the user is dragging, the browser-set cursor shouldn't be used
     * Instead the cursor should change based on what action would be performed when they release at the given location
     * However, the browser-set cursor also needs to be tracked, so this handles that as well
     *
     * @param cursorType the actual cursor type (should be the result of {@link MCEFDragContext#getActualCursor()} if you're just trying to see the current cursor)
     * @return the drag operation modified cursor if dragging, or the actual cursor if not
     */
    public int getVirtualCursor(int cursorType) {
        actualCursor = cursorType;
        if (cursorOverride != -1) cursorType = cursorOverride;
        return cursorType;
    }

    /**
     * Checks if a drag operation is currently happening
     *
     * @return true if the user is dragging, elsewise false
     */
    public boolean isDragging() {
        return dragData != null;
    }

    /**
     * Gets the {@link CefDragData} of the current drag operation
     *
     * @return the current drag operation's data
     */
    public CefDragData getDragData() {
        return dragData;
    }

    /**
     * Gets the allowed operation mask for this drag event
     *
     * @return CEF drag operation bits: 0 none, 1 copy, 2 link, 4 generic, 8 private, 16 move, 32 delete, -1 any
     */
    public int getMask() {
        return dragMask;
    }

    /**
     * Gets the browser-set cursor
     *
     * @return the cursor that has been set by the browser, disregarding drag operations
     */
    public int getActualCursor() {
        return actualCursor;
    }

    public void startDragging(CefDragData dragData, int mask) {
        this.dragData = dragData;
        this.dragMask = mask;
    }

    public void stopDragging() {
        if (dragData != null) dragData.dispose();
        dragData = null;
        dragMask = 0;
        cursorOverride = -1;
    }

    public boolean updateCursor(int operation) {
        if (dragData == null) return false;

        int currentOverride = cursorOverride;

        // cef_drag_operations_mask_t: NONE 0, COPY 1, LINK 2, GENERIC 4, PRIVATE 8, MOVE 16, DELETE 32
        cursorOverride = switch (operation) {
            case 0 -> CefCursorType.NO_DROP.ordinal();
            case 1 -> CefCursorType.COPY.ordinal();
            case 2 -> CefCursorType.ALIAS.ordinal();
            case 4, 8, 16 -> CefCursorType.MOVE.ordinal();
            default -> -1;
        };

        return currentOverride != cursorOverride && cursorOverride != -1;
    }
}
