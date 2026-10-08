package net.geforcemods.securitycraft.fabric.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Minimal stand-in for NeoForge's event bus, used only for SecurityCraft's own events (like OwnershipEvent), so that the
 * many {@code NeoForge.EVENT_BUS.post(...)} call sites keep compiling. Vanilla and Fabric events are not routed through
 * this.
 */
public final class NeoForge {
	public static final EventBus EVENT_BUS = new EventBus();

	private NeoForge() {}

	public static final class EventBus {
		private final Map<Class<?>, List<Consumer<Object>>> listeners = new ConcurrentHashMap<>();

		private EventBus() {}

		@SuppressWarnings("unchecked")
		public <T> void addListener(Class<T> eventClass, Consumer<? super T> listener) {
			listeners.computeIfAbsent(eventClass, c -> new ArrayList<>()).add((Consumer<Object>) listener);
		}

		public <T> T post(T event) {
			List<Consumer<Object>> eventListeners = listeners.get(event.getClass());

			if (eventListeners != null) {
				for (Consumer<Object> listener : eventListeners) {
					listener.accept(event);
				}
			}

			return event;
		}
	}
}
