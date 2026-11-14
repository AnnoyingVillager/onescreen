package dev.onescreen.client.debug;

public final class ClientScreenDebugState {
	private static volatile boolean enabled;

	private ClientScreenDebugState() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void setEnabled(final boolean nextEnabled) {
		enabled = nextEnabled;
	}

	public static void reset() {
		enabled = false;
	}
}