package io.github.fishgames.athanorium.base.platform;

/** Holder for the loader implementation of {@link Platform}. */
public final class Services {
    private static Platform platform;

    private Services() {
    }

    public static void set(Platform p) {
        platform = p;
    }

    public static Platform platform() {
        if (platform == null) {
            throw new IllegalStateException("Platform not set; the loader module must call Services.set first");
        }
        return platform;
    }
}
