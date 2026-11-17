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

import org.lwjgl.sdl.SDLKeycode;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Converts Minecraft 1.21.1's GLFW input codes into the SDL3 codes that the java-cef natives expect
 * (mouse buttons, key modifier bits, USB HID scancodes and SDL keycodes).
 */
final class SdlInput {
    private SdlInput() {
    }

    /** GLFW mouse button (0 left, 1 right, 2 middle) to SDL button, or -1. */
    static int button(int glfwButton) {
        return switch (glfwButton) {
            case GLFW_MOUSE_BUTTON_LEFT -> 1;
            case GLFW_MOUSE_BUTTON_MIDDLE -> 2;
            case GLFW_MOUSE_BUTTON_RIGHT -> 3;
            default -> -1;
        };
    }

    /** java-cef's pressed-button flags (BUTTON1 left, BUTTON2 middle, BUTTON3 right). */
    static int buttonMask(int glfwButton) {
        return switch (glfwButton) {
            case GLFW_MOUSE_BUTTON_LEFT -> 0x10;
            case GLFW_MOUSE_BUTTON_MIDDLE -> 0x20;
            case GLFW_MOUSE_BUTTON_RIGHT -> 0x40;
            default -> 0;
        };
    }

    static int modifiers(int glfwMods) {
        int m = 0;
        if ((glfwMods & GLFW_MOD_SHIFT) != 0) m |= SDLKeycode.SDL_KMOD_LSHIFT;
        if ((glfwMods & GLFW_MOD_CONTROL) != 0) m |= SDLKeycode.SDL_KMOD_LCTRL;
        if ((glfwMods & GLFW_MOD_ALT) != 0) m |= SDLKeycode.SDL_KMOD_LALT;
        if ((glfwMods & GLFW_MOD_SUPER) != 0) m |= SDLKeycode.SDL_KMOD_LGUI;
        if ((glfwMods & GLFW_MOD_CAPS_LOCK) != 0) m |= SDLKeycode.SDL_KMOD_CAPS;
        if ((glfwMods & GLFW_MOD_NUM_LOCK) != 0) m |= SDLKeycode.SDL_KMOD_NUM;
        return m;
    }

    /** SDL scancode (USB HID usage id) of a GLFW key, 0 if unknown. */
    static int scancode(int key) {
        if (key >= GLFW_KEY_A && key <= GLFW_KEY_Z) return 4 + (key - GLFW_KEY_A);
        if (key >= GLFW_KEY_1 && key <= GLFW_KEY_9) return 30 + (key - GLFW_KEY_1);
        if (key >= GLFW_KEY_F1 && key <= GLFW_KEY_F12) return 58 + (key - GLFW_KEY_F1);
        if (key >= GLFW_KEY_F13 && key <= GLFW_KEY_F24) return 104 + (key - GLFW_KEY_F13);
        if (key >= GLFW_KEY_KP_1 && key <= GLFW_KEY_KP_9) return 89 + (key - GLFW_KEY_KP_1);
        return switch (key) {
            case GLFW_KEY_0 -> 39;
            case GLFW_KEY_ENTER -> 40;
            case GLFW_KEY_ESCAPE -> 41;
            case GLFW_KEY_BACKSPACE -> 42;
            case GLFW_KEY_TAB -> 43;
            case GLFW_KEY_SPACE -> 44;
            case GLFW_KEY_MINUS -> 45;
            case GLFW_KEY_EQUAL -> 46;
            case GLFW_KEY_LEFT_BRACKET -> 47;
            case GLFW_KEY_RIGHT_BRACKET -> 48;
            case GLFW_KEY_BACKSLASH -> 49;
            case GLFW_KEY_SEMICOLON -> 51;
            case GLFW_KEY_APOSTROPHE -> 52;
            case GLFW_KEY_GRAVE_ACCENT -> 53;
            case GLFW_KEY_COMMA -> 54;
            case GLFW_KEY_PERIOD -> 55;
            case GLFW_KEY_SLASH -> 56;
            case GLFW_KEY_CAPS_LOCK -> 57;
            case GLFW_KEY_PRINT_SCREEN -> 70;
            case GLFW_KEY_SCROLL_LOCK -> 71;
            case GLFW_KEY_PAUSE -> 72;
            case GLFW_KEY_INSERT -> 73;
            case GLFW_KEY_HOME -> 74;
            case GLFW_KEY_PAGE_UP -> 75;
            case GLFW_KEY_DELETE -> 76;
            case GLFW_KEY_END -> 77;
            case GLFW_KEY_PAGE_DOWN -> 78;
            case GLFW_KEY_RIGHT -> 79;
            case GLFW_KEY_LEFT -> 80;
            case GLFW_KEY_DOWN -> 81;
            case GLFW_KEY_UP -> 82;
            case GLFW_KEY_NUM_LOCK -> 83;
            case GLFW_KEY_KP_DIVIDE -> 84;
            case GLFW_KEY_KP_MULTIPLY -> 85;
            case GLFW_KEY_KP_SUBTRACT -> 86;
            case GLFW_KEY_KP_ADD -> 87;
            case GLFW_KEY_KP_ENTER -> 88;
            case GLFW_KEY_KP_0 -> 98;
            case GLFW_KEY_KP_DECIMAL -> 99;
            case GLFW_KEY_WORLD_2 -> 100;
            case GLFW_KEY_MENU -> 101;
            case GLFW_KEY_KP_EQUAL -> 103;
            case GLFW_KEY_LEFT_CONTROL -> 224;
            case GLFW_KEY_LEFT_SHIFT -> 225;
            case GLFW_KEY_LEFT_ALT -> 226;
            case GLFW_KEY_LEFT_SUPER -> 227;
            case GLFW_KEY_RIGHT_CONTROL -> 228;
            case GLFW_KEY_RIGHT_SHIFT -> 229;
            case GLFW_KEY_RIGHT_ALT -> 230;
            case GLFW_KEY_RIGHT_SUPER -> 231;
            default -> 0;
        };
    }

    /**
     * SDL keycode of a GLFW key: printable keys are their unshifted US-layout character (letters in lower case),
     * the others are their scancode with SDLK_SCANCODE_MASK. Text itself is sent separately as typed characters.
     */
    static int keycode(int key) {
        if (key >= GLFW_KEY_A && key <= GLFW_KEY_Z) return 'a' + (key - GLFW_KEY_A);
        if (key >= GLFW_KEY_SPACE && key <= GLFW_KEY_GRAVE_ACCENT) return key; // US-layout punctuation and digits are ASCII
        return switch (key) {
            case GLFW_KEY_ENTER -> SDLKeycode.SDLK_RETURN;
            case GLFW_KEY_ESCAPE -> SDLKeycode.SDLK_ESCAPE;
            case GLFW_KEY_BACKSPACE -> SDLKeycode.SDLK_BACKSPACE;
            case GLFW_KEY_TAB -> SDLKeycode.SDLK_TAB;
            case GLFW_KEY_DELETE -> SDLKeycode.SDLK_DELETE;
            default -> {
                int sc = scancode(key);
                yield sc == 0 ? 0 : sc | SDLKeycode.SDLK_SCANCODE_MASK;
            }
        };
    }

    /** The character of a printable keycode (what the natives expect as key char), else 0. */
    static char keyChar(int sdlKeycode) {
        return (sdlKeycode & (SDLKeycode.SDLK_SCANCODE_MASK | SDLKeycode.SDLK_EXTENDED_MASK)) == 0 ? (char) sdlKeycode : '\0';
    }
}
