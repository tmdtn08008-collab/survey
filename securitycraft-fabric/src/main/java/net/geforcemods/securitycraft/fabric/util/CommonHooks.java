package net.geforcemods.securitycraft.fabric.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;

/**
 * The parts of NeoForge's CommonHooks that SecurityCraft uses.
 */
public final class CommonHooks {
	private static final Pattern URL_PATTERN = Pattern.compile("((?:[a-z0-9]{2,}:\\/\\/)?(?:(?:[0-9]{1,3}\\.){3}[0-9]{1,3}|(?:[-\\w_]{1,}\\.[a-z]{2,}?))(?::[0-9]{1,5})?.*?(?=[!\"§ \n]|$))", Pattern.CASE_INSENSITIVE);
	private static final ThreadLocal<Player> CRAFTING_PLAYER = new ThreadLocal<>();

	private CommonHooks() {}

	/**
	 * Turns every URL in the given text into a clickable, underlined link.
	 */
	public static Component newChatWithLinks(String text) {
		MutableComponent result = Component.empty();
		Matcher matcher = URL_PATTERN.matcher(text);
		int lastEnd = 0;

		while (matcher.find()) {
			if (matcher.start() > lastEnd)
				result.append(text.substring(lastEnd, matcher.start()));

			String url = matcher.group();
			String target = url;

			try {
				if (new URI(url).getScheme() == null)
					target = "http://" + url;
			}
			catch (URISyntaxException e) {
				result.append(url);
				lastEnd = matcher.end();
				continue;
			}

			String clickTarget = target;

			result.append(Component.literal(url).withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, clickTarget)).withUnderlined(true).withColor(ChatFormatting.BLUE)));
			lastEnd = matcher.end();
		}

		if (lastEnd < text.length())
			result.append(text.substring(lastEnd));

		return result;
	}

	/**
	 * @return The player taking the result of a crafting recipe on this thread, or null. Set by ResultSlotMixin.
	 */
	public static Player getCraftingPlayer() {
		return CRAFTING_PLAYER.get();
	}

	public static void setCraftingPlayer(Player player) {
		CRAFTING_PLAYER.set(player);
	}
}
