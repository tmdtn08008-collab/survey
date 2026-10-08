package net.geforcemods.securitycraft.fabric.registry;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import com.mojang.brigadier.arguments.ArgumentType;

import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Fabric stand-in for NeoForge's DeferredRegister. Entries are collected when the owning class is loaded and are put into the
 * vanilla registry when {@link #register()} is called from the mod initializer, in the order they were declared.
 */
public class DeferredRegister<T> {
	private final ResourceKey<? extends Registry<T>> registryKey;
	private final String namespace;
	private final Map<DeferredHolder<T, ?>, Function<ResourceLocation, ? extends T>> entries = new LinkedHashMap<>();
	private final Collection<DeferredHolder<T, ? extends T>> entriesView = Collections.unmodifiableSet(entries.keySet());
	private boolean registered = false;

	protected DeferredRegister(ResourceKey<? extends Registry<T>> registryKey, String namespace) {
		this.registryKey = registryKey;
		this.namespace = namespace;
	}

	public static <T> DeferredRegister<T> create(ResourceKey<? extends Registry<T>> registryKey, String namespace) {
		return new DeferredRegister<>(registryKey, namespace);
	}

	public static Blocks createBlocks(String namespace) {
		return new Blocks(namespace);
	}

	public static Items createItems(String namespace) {
		return new Items(namespace);
	}

	public static DataComponents createDataComponents(ResourceKey<Registry<DataComponentType<?>>> registryKey, String namespace) {
		return new DataComponents(registryKey, namespace);
	}

	public static DataComponents createDataComponents(String namespace) {
		return createDataComponents(Registries.DATA_COMPONENT_TYPE, namespace);
	}

	/**
	 * Entity data serializers have no registry in vanilla; they are added to {@link EntityDataSerializers} by numeric id, so
	 * client and server must register them in the same order (which declaration order guarantees).
	 */
	public static EntityDataSerializers_ createEntityDataSerializers(String namespace) {
		return new EntityDataSerializers_(namespace);
	}

	public static ArgumentTypes createArgumentTypes(String namespace) {
		return new ArgumentTypes(namespace);
	}

	public <I extends T> DeferredHolder<T, I> register(String name, Supplier<? extends I> sup) {
		return register(name, key -> sup.get());
	}

	public <I extends T> DeferredHolder<T, I> register(String name, Function<ResourceLocation, ? extends I> func) {
		if (registered)
			throw new IllegalStateException("Cannot register new entries to DeferredRegister after it has been registered");

		ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, name);
		DeferredHolder<T, I> holder = createHolder(registryKey, id);

		if (entries.putIfAbsent(holder, func) != null)
			throw new IllegalArgumentException("Duplicate registration " + name);

		return holder;
	}

	protected <I extends T> DeferredHolder<T, I> createHolder(ResourceKey<? extends Registry<T>> registryKey, ResourceLocation id) {
		return DeferredHolder.create(registryKey, id);
	}

	/**
	 * Registers all entries. Must be called exactly once, from the mod initializer.
	 */
	@SuppressWarnings("unchecked")
	public void register() {
		if (registered)
			throw new IllegalStateException("DeferredRegister for " + registryKey.location() + " was already registered");

		registered = true;

		Registry<T> registry = (Registry<T>) BuiltInRegistries.REGISTRY.get(registryKey.location());

		if (registry == null)
			throw new IllegalStateException("Unknown registry " + registryKey.location());

		entries.forEach((holder, factory) -> holder.bind(Registry.registerForHolder(registry, holder.getKey(), factory.apply(holder.getId()))));
	}

	protected void forEachEntry(java.util.function.BiConsumer<DeferredHolder<T, ?>, Function<ResourceLocation, ? extends T>> action) {
		entries.forEach(action);
	}

	protected void markRegistered() {
		if (registered)
			throw new IllegalStateException("DeferredRegister for " + registryKey.location() + " was already registered");

		registered = true;
	}

	public Collection<DeferredHolder<T, ? extends T>> getEntries() {
		return entriesView;
	}

	public ResourceKey<? extends Registry<T>> getRegistryKey() {
		return registryKey;
	}

	public ResourceLocation getRegistryName() {
		return registryKey.location();
	}

	public String getNamespace() {
		return namespace;
	}

	public static class Blocks extends DeferredRegister<Block> {
		protected Blocks(String namespace) {
			super(Registries.BLOCK, namespace);
		}

		@Override
		@SuppressWarnings("unchecked")
		public <B extends Block> DeferredBlock<B> register(String name, Function<ResourceLocation, ? extends B> func) {
			return (DeferredBlock<B>) super.register(name, func);
		}

		@Override
		public <B extends Block> DeferredBlock<B> register(String name, Supplier<? extends B> sup) {
			return register(name, key -> sup.get());
		}

		public <B extends Block> DeferredBlock<B> registerBlock(String name, Function<BlockBehaviour.Properties, ? extends B> func, BlockBehaviour.Properties props) {
			return register(name, () -> func.apply(props));
		}

		public <B extends Block> DeferredBlock<B> registerBlock(String name, Function<BlockBehaviour.Properties, ? extends B> func) {
			return registerBlock(name, func, BlockBehaviour.Properties.of());
		}

		public DeferredBlock<Block> registerSimpleBlock(String name, BlockBehaviour.Properties props) {
			return registerBlock(name, Block::new, props);
		}

		public DeferredBlock<Block> registerSimpleBlock(String name) {
			return registerSimpleBlock(name, BlockBehaviour.Properties.of());
		}

		@Override
		@SuppressWarnings("unchecked")
		protected <I extends Block> DeferredHolder<Block, I> createHolder(ResourceKey<? extends Registry<Block>> registryKey, ResourceLocation id) {
			return (DeferredHolder<Block, I>) new DeferredBlock<>(ResourceKey.create(registryKey, id));
		}
	}

	public static class Items extends DeferredRegister<Item> {
		protected Items(String namespace) {
			super(Registries.ITEM, namespace);
		}

		@Override
		@SuppressWarnings("unchecked")
		public <I extends Item> DeferredItem<I> register(String name, Function<ResourceLocation, ? extends I> func) {
			return (DeferredItem<I>) super.register(name, func);
		}

		@Override
		public <I extends Item> DeferredItem<I> register(String name, Supplier<? extends I> sup) {
			return register(name, key -> sup.get());
		}

		public DeferredItem<BlockItem> registerSimpleBlockItem(String name, Supplier<? extends Block> block, Item.Properties properties) {
			return register(name, key -> new BlockItem(block.get(), properties));
		}

		public DeferredItem<BlockItem> registerSimpleBlockItem(String name, Supplier<? extends Block> block) {
			return registerSimpleBlockItem(name, block, new Item.Properties());
		}

		public DeferredItem<BlockItem> registerSimpleBlockItem(DeferredBlock<?> block, Item.Properties properties) {
			return registerSimpleBlockItem(block.getId().getPath(), block, properties);
		}

		public DeferredItem<BlockItem> registerSimpleBlockItem(DeferredBlock<?> block) {
			return registerSimpleBlockItem(block, new Item.Properties());
		}

		public <I extends Item> DeferredItem<I> registerItem(String name, Function<Item.Properties, ? extends I> func, Item.Properties props) {
			return register(name, key -> func.apply(props));
		}

		public <I extends Item> DeferredItem<I> registerItem(String name, Function<Item.Properties, ? extends I> func) {
			return registerItem(name, func, new Item.Properties());
		}

		public DeferredItem<Item> registerSimpleItem(String name, Item.Properties props) {
			return registerItem(name, Item::new, props);
		}

		public DeferredItem<Item> registerSimpleItem(String name) {
			return registerSimpleItem(name, new Item.Properties());
		}

		@Override
		@SuppressWarnings("unchecked")
		protected <I extends Item> DeferredHolder<Item, I> createHolder(ResourceKey<? extends Registry<Item>> registryKey, ResourceLocation id) {
			return (DeferredHolder<Item, I>) new DeferredItem<>(ResourceKey.create(registryKey, id));
		}
	}

	public static class DataComponents extends DeferredRegister<DataComponentType<?>> {
		protected DataComponents(ResourceKey<Registry<DataComponentType<?>>> registryKey, String namespace) {
			super(registryKey, namespace);
		}

		/**
		 * Unlike NeoForge, this registers the component type right away and returns it directly, because vanilla's
		 * DataComponentHolder methods only accept DataComponentType (NeoForge adds Supplier overloads). This is safe since
		 * the owning class is first loaded from the mod initializer, while registries are still open.
		 */
		public <D> DataComponentType<D> registerComponentType(String name, UnaryOperator<DataComponentType.Builder<D>> builder) {
			DataComponentType<D> type = builder.apply(DataComponentType.<D>builder()).build();

			Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, ResourceLocation.fromNamespaceAndPath(getNamespace(), name), type);
			return type;
		}
	}

	/**
	 * See {@link DeferredRegister#createEntityDataSerializers}. The underscore avoids clashing with the vanilla class name.
	 */
	public static class EntityDataSerializers_ extends DeferredRegister<EntityDataSerializer<?>> {
		private static final ResourceKey<Registry<EntityDataSerializer<?>>> PSEUDO_REGISTRY_KEY = ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("securitycraft", "entity_data_serializers"));

		protected EntityDataSerializers_(String namespace) {
			super(PSEUDO_REGISTRY_KEY, namespace);
		}

		@Override
		public void register() {
			markRegistered();
			forEachEntry((holder, factory) -> {
				EntityDataSerializer<?> serializer = factory.apply(holder.getId());

				EntityDataSerializers.registerSerializer(serializer);
				holder.bind(Holder.direct(serializer));
			});
		}
	}

	public static class ArgumentTypes extends DeferredRegister<ArgumentTypeInfo<?, ?>> {
		protected ArgumentTypes(String namespace) {
			super(Registries.COMMAND_ARGUMENT_TYPE, namespace);
		}

		/**
		 * Registers an argument type through Fabric API, which also takes care of syncing it to clients that have the mod.
		 */
		@SuppressWarnings({"rawtypes", "unchecked"})
		public <A extends ArgumentType<?>, I extends ArgumentTypeInfo<A, ?>> DeferredHolder<ArgumentTypeInfo<?, ?>, I> registerArgumentType(String name, Class<? extends A> clazz, Supplier<I> info) {
			return register(name, id -> {
				I created = info.get();

				ArgumentTypeRegistry.registerArgumentType(id, (Class) clazz, (ArgumentTypeInfo) created);
				return created;
			});
		}

		@Override
		@SuppressWarnings({"rawtypes", "unchecked"})
		public void register() {
			markRegistered();
			forEachEntry((holder, factory) -> {
				factory.apply(holder.getId());
				holder.bind((Holder) BuiltInRegistries.COMMAND_ARGUMENT_TYPE.getHolder((ResourceKey) holder.getKey()).orElseThrow());
			});
		}
	}
}
