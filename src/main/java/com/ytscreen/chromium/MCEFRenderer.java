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

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL12.*;

/**
 * The OpenGL texture a browser paints into. All methods run on the render thread (Chromium's message loop is pumped
 * there, so paint events arrive on it too). The texture is created with the first frame and deleted when the browser
 * closes; afterwards nothing is uploaded any more.
 */
public class MCEFRenderer {
    private final boolean transparent;
    private int textureId;
    private boolean closed;

    protected MCEFRenderer(boolean transparent) {
        this.transparent = transparent;
    }

    /** GL texture name, 0 before the first frame and after closing. */
    public int getTextureID() {
        return textureId;
    }

    public boolean isTransparent() {
        return transparent;
    }

    protected void cleanup() {
        closed = true;
        if (textureId != 0) {
            RenderSystem.deleteTexture(textureId);
            textureId = 0;
        }
    }

    /** Uploads a whole frame (also when the size changed). */
    protected void uploadFull(ByteBuffer buffer, int width, int height) {
        if (closed) return;
        if (textureId == 0) {
            textureId = glGenTextures();
            RenderSystem.bindTexture(textureId);
            RenderSystem.texParameter(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            RenderSystem.texParameter(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            RenderSystem.texParameter(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            RenderSystem.texParameter(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        } else {
            RenderSystem.bindTexture(textureId);
        }
        unpack(width, 0, 0);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, buffer);
        unpack(0, 0, 0);
    }

    /**
     * Uploads the rectangle (x, y, w, h) of {@code buffer} (an image {@code stride} pixels wide, read from
     * (srcX, srcY)) to (x, y) of the texture. The texture must exist.
     */
    protected void uploadRect(ByteBuffer buffer, int stride, int srcX, int srcY, int x, int y, int w, int h) {
        if (closed || textureId == 0 || w <= 0 || h <= 0) return;
        RenderSystem.bindTexture(textureId);
        unpack(stride, srcX, srcY);
        glTexSubImage2D(GL_TEXTURE_2D, 0, x, y, w, h, GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, buffer);
        unpack(0, 0, 0);
    }

    /** Pixel unpack state; always reset to 0 afterwards so Minecraft's own uploads are not affected. */
    private static void unpack(int rowLength, int skipPixels, int skipRows) {
        RenderSystem.pixelStore(GL_UNPACK_ROW_LENGTH, rowLength);
        RenderSystem.pixelStore(GL_UNPACK_SKIP_PIXELS, skipPixels);
        RenderSystem.pixelStore(GL_UNPACK_SKIP_ROWS, skipRows);
    }
}
