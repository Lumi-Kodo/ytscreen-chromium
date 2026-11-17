package org.lwjgl.sdl;

/**
 * Minimal stand-in for LWJGL's SDL3 binding class of the same name (see {@link SDLMouse}): the key codes and key
 * modifier bits that the java-cef natives read through JNI, with SDL3's values.
 */
public final class SDLKeycode {
    public static final int SDLK_EXTENDED_MASK = 1 << 29;
    public static final int SDLK_SCANCODE_MASK = 1 << 30;

    public static final int SDLK_RETURN = 0x0000000d;
    public static final int SDLK_ESCAPE = 0x0000001b;
    public static final int SDLK_BACKSPACE = 0x00000008;
    public static final int SDLK_TAB = 0x00000009;
    public static final int SDLK_DELETE = 0x0000007f;
    public static final int SDLK_RIGHT = 0x4000004f;
    public static final int SDLK_LEFT = 0x40000050;
    public static final int SDLK_DOWN = 0x40000051;
    public static final int SDLK_UP = 0x40000052;
    public static final int SDLK_KP_ENTER = 0x40000058;

    public static final int SDL_KMOD_LSHIFT = 0x0001;
    public static final int SDL_KMOD_RSHIFT = 0x0002;
    public static final int SDL_KMOD_LCTRL = 0x0040;
    public static final int SDL_KMOD_RCTRL = 0x0080;
    public static final int SDL_KMOD_LALT = 0x0100;
    public static final int SDL_KMOD_RALT = 0x0200;
    public static final int SDL_KMOD_LGUI = 0x0400;
    public static final int SDL_KMOD_RGUI = 0x0800;
    public static final int SDL_KMOD_NUM = 0x1000;
    public static final int SDL_KMOD_CAPS = 0x2000;
    public static final int SDL_KMOD_CTRL = SDL_KMOD_LCTRL | SDL_KMOD_RCTRL;
    public static final int SDL_KMOD_SHIFT = SDL_KMOD_LSHIFT | SDL_KMOD_RSHIFT;
    public static final int SDL_KMOD_ALT = SDL_KMOD_LALT | SDL_KMOD_RALT;
    public static final int SDL_KMOD_GUI = SDL_KMOD_LGUI | SDL_KMOD_RGUI;

    private SDLKeycode() {
    }
}
