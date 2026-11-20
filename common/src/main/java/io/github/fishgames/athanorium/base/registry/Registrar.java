package io.github.fishgames.athanorium.base.registry;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

import java.util.function.Supplier;

/** Registers game objects with the loader. Fabric registers at once, Forge defers until its registry events. */
public interface Registrar {
    <R, T extends R> Supplier<T> register(ResourceKey<Registry<R>> registry, String name, Supplier<T> factory);
}
