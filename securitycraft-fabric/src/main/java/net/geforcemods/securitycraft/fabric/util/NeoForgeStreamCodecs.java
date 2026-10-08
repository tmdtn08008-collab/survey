package net.geforcemods.securitycraft.fabric.util;

import java.util.function.Function;

import com.mojang.datafixers.util.Function7;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * The parts of NeoForge's NeoForgeStreamCodecs that SecurityCraft uses.
 */
public final class NeoForgeStreamCodecs {
	private NeoForgeStreamCodecs() {}

	/**
	 * Same wire format as {@link FriendlyByteBuf#writeEnum}.
	 */
	public static <B extends FriendlyByteBuf, V extends Enum<V>> StreamCodec<B, V> enumCodec(Class<V> enumClass) {
		return new StreamCodec<>() {
			@Override
			public V decode(B buf) {
				return buf.readEnum(enumClass);
			}

			@Override
			public void encode(B buf, V value) {
				buf.writeEnum(value);
			}
		};
	}

	public static <B, C, T1, T2, T3, T4, T5, T6, T7> StreamCodec<B, C> composite(StreamCodec<? super B, T1> codec1, Function<C, T1> getter1, StreamCodec<? super B, T2> codec2, Function<C, T2> getter2, StreamCodec<? super B, T3> codec3, Function<C, T3> getter3, StreamCodec<? super B, T4> codec4, Function<C, T4> getter4, StreamCodec<? super B, T5> codec5, Function<C, T5> getter5, StreamCodec<? super B, T6> codec6, Function<C, T6> getter6, StreamCodec<? super B, T7> codec7, Function<C, T7> getter7, Function7<T1, T2, T3, T4, T5, T6, T7, C> factory) {
		return new StreamCodec<>() {
			@Override
			public C decode(B buf) {
				T1 t1 = codec1.decode(buf);
				T2 t2 = codec2.decode(buf);
				T3 t3 = codec3.decode(buf);
				T4 t4 = codec4.decode(buf);
				T5 t5 = codec5.decode(buf);
				T6 t6 = codec6.decode(buf);
				T7 t7 = codec7.decode(buf);

				return factory.apply(t1, t2, t3, t4, t5, t6, t7);
			}

			@Override
			public void encode(B buf, C value) {
				codec1.encode(buf, getter1.apply(value));
				codec2.encode(buf, getter2.apply(value));
				codec3.encode(buf, getter3.apply(value));
				codec4.encode(buf, getter4.apply(value));
				codec5.encode(buf, getter5.apply(value));
				codec6.encode(buf, getter6.apply(value));
				codec7.encode(buf, getter7.apply(value));
			}
		};
	}
}
