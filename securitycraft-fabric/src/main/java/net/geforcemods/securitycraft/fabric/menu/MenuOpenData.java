package net.geforcemods.securitycraft.fabric.menu;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The extra data a SecurityCraft menu sends to the client when it opens, as raw bytes. This mirrors NeoForge's
 * "writeClientSideData" buffer, so the existing menu factories can keep reading from a {@link RegistryFriendlyByteBuf}.
 */
public record MenuOpenData(byte[] bytes) {
	public static final MenuOpenData EMPTY = new MenuOpenData(new byte[0]);
	public static final StreamCodec<RegistryFriendlyByteBuf, MenuOpenData> STREAM_CODEC = ByteBufCodecs.BYTE_ARRAY.<RegistryFriendlyByteBuf>cast().map(MenuOpenData::new, MenuOpenData::bytes);

	public static MenuOpenData write(RegistryAccess registryAccess, java.util.function.Consumer<RegistryFriendlyByteBuf> writer) {
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess);

		try {
			writer.accept(buf);

			byte[] bytes = new byte[buf.readableBytes()];

			buf.readBytes(bytes);
			return new MenuOpenData(bytes);
		}
		finally {
			buf.release();
		}
	}

	public RegistryFriendlyByteBuf toBuffer(RegistryAccess registryAccess) {
		return new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes), registryAccess);
	}
}
