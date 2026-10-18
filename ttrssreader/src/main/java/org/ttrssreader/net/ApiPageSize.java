package org.ttrssreader.net;

public final class ApiPageSize {
	private ApiPageSize() {
	}

	public static boolean isValid(Object value) {
		if (!(value instanceof String))
			return false;
		try {
			return Integer.parseInt((String) value) > 0;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	static int headlineLimit(int configured, int apiLevel, long remaining) {
		return (int) Math.min(Math.min(configured, apiLevel < 6 ? 60 : JSONConnector.PARAM_LIMIT_MAX_VALUE), remaining);
	}
}
