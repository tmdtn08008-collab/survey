package net.geforcemods.securitycraft.fabric.model;

import java.util.IdentityHashMap;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Fabric stand-in for NeoForge's ModelData: immutable extra data a block entity hands to its block's model. On Fabric it
 * travels to the model as the block entity's render data (RenderDataBlockEntity#getRenderData).
 */
public final class ModelData {
	public static final ModelData EMPTY = new ModelData(Map.of());
	private final Map<ModelProperty<?>, Object> properties;

	private ModelData(Map<ModelProperty<?>, Object> properties) {
		this.properties = properties;
	}

	@Nullable
	@SuppressWarnings("unchecked")
	public <T> T get(ModelProperty<T> property) {
		return (T) properties.get(property);
	}

	public boolean has(ModelProperty<?> property) {
		return properties.containsKey(property);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private final Map<ModelProperty<?>, Object> properties = new IdentityHashMap<>();

		private Builder() {}

		public <T> Builder with(ModelProperty<T> property, T value) {
			properties.put(property, value);
			return this;
		}

		public ModelData build() {
			return properties.isEmpty() ? EMPTY : new ModelData(new IdentityHashMap<>(properties));
		}
	}
}
