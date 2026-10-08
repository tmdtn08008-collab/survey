package net.geforcemods.securitycraft.fabric.registry;

import java.util.Optional;
import java.util.function.Supplier;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * Fabric stand-in for NeoForge's DeferredHolder: a reference to an object that is registered later, during mod
 * initialization. Unlike NeoForge's class this deliberately does not implement {@link Holder}: vanilla compares holders by
 * identity in places like ItemStack#is(Holder), so passing this where a Holder is expected would silently fail. Use
 * {@link #getDelegate()} when a real Holder is needed.
 */
public class DeferredHolder<R, T extends R> implements Supplier<T> {
	protected final ResourceKey<R> key;
	private Holder<R> holder;

	protected DeferredHolder(ResourceKey<R> key) {
		this.key = key;
	}

	public static <R, T extends R> DeferredHolder<R, T> create(ResourceKey<? extends Registry<R>> registryKey, ResourceLocation id) {
		return new DeferredHolder<>(ResourceKey.create(registryKey, id));
	}

	void bind(Holder<R> holder) {
		this.holder = holder;
	}

	/**
	 * @return The vanilla holder of the registered object
	 */
	@SuppressWarnings("unchecked")
	public Holder<R> getDelegate() {
		if (holder == null) {
			Registry<R> registry = (Registry<R>) BuiltInRegistries.REGISTRY.get(key.registry());

			if (registry != null)
				holder = registry.getHolder(key).orElse(null);

			if (holder == null)
				throw new IllegalStateException("Registry object not present: " + key);
		}

		return holder;
	}

	@SuppressWarnings("unchecked")
	public T value() {
		return (T) getDelegate().value();
	}

	@Override
	public T get() {
		return value();
	}

	public Optional<T> asOptional() {
		return isBound() ? Optional.of(value()) : Optional.empty();
	}

	public boolean isBound() {
		if (holder == null) {
			try {
				getDelegate();
			}
			catch (IllegalStateException e) {
				return false;
			}
		}

		return holder.isBound();
	}

	public ResourceLocation getId() {
		return key.location();
	}

	public ResourceKey<R> getKey() {
		return key;
	}

	public Optional<ResourceKey<R>> unwrapKey() {
		return Optional.of(key);
	}

	@Override
	public boolean equals(Object obj) {
		return this == obj || obj instanceof DeferredHolder<?, ?> other && key.equals(other.key);
	}

	@Override
	public int hashCode() {
		return key.hashCode();
	}

	@Override
	public String toString() {
		return "DeferredHolder{" + key + "}";
	}
}
