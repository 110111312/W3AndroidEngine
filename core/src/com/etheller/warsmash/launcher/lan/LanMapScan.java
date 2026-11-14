package com.etheller.warsmash.launcher.lan;

import java.util.regex.Pattern;

/** Finds what in a map's script a LAN game cannot yet run the same on every device. */
public final class LanMapScan {
	private static final Pattern SYNC = Pattern
			.compile("\\b(Sync[A-Z][A-Za-z]*|TriggerSync[A-Z][A-Za-z]*|BlzSendSyncData|BlzGetTriggerSync[A-Za-z]*"
					+ "|GetTriggerSync[A-Za-z]*|TriggerRegisterPlayerSyncEvent)\\b");

	private LanMapScan() {
	}

	/** Whether the script calls a Sync native. */
	public static boolean usesSync(final String script) {
		return (script != null) && SYNC.matcher(script).find();
	}
}
