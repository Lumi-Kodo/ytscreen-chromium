package org.lwjgl.sdl;

/**
 * Minimal stand-in for LWJGL's SDL3 binding class of the same name. The java-cef natives (built for Minecraft versions
 * that use SDL) look up these constants through JNI; Minecraft 1.21.1 ships GLFW only, so without this class every
 * mouse event would be dropped. Only the constants the natives read are defined, with SDL3's values.
 */
public final class SDLMouse {
    public static final int SDL_BUTTON_LEFT = 1;
    public static final int SDL_BUTTON_MIDDLE = 2;
    public static final int SDL_BUTTON_RIGHT = 3;

    private SDLMouse() {
    }
}
