/*
 * Copyright (c) 2015, Nils Braden
 *
 * This file is part of ttrss-reader-fork. This program is free software; you
 * can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation;
 * either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details. You should have received a
 * copy of the GNU General Public License along with this program; If
 * not, see http://www.gnu.org/licenses/.
 */

package org.ttrssreader.net;

/**
 * Calculates the HTTP timeout used for backend API calls.
 */
public final class ApiTimeouts {

	/**
	 * OkHttp rejects timeouts larger than Integer.MAX_VALUE milliseconds.
	 */
	static final long MAX_TIMEOUT_SECONDS = Integer.MAX_VALUE / 1000L;

	private ApiTimeouts() {
	}

	/**
	 * @param lazyServer     whether the lazy server option is enabled
	 * @param timeoutSeconds regular timeout in seconds
	 * @param lazyMinutes    lazy server timeout in minutes
	 * @return the effective timeout in seconds, always between 1 and {@link #MAX_TIMEOUT_SECONDS}
	 */
	public static long effectiveTimeoutSeconds(boolean lazyServer, int timeoutSeconds, int lazyMinutes) {
		long seconds = lazyServer ? lazyMinutes * 60L : timeoutSeconds;
		if (seconds < 1)
			seconds = 1;
		return Math.min(seconds, MAX_TIMEOUT_SECONDS);
	}

	/**
	 * @return true if the value is a positive integer (as entered in the preferences)
	 */
	public static boolean isValidTimeout(Object value) {
		if (value == null)
			return false;
		try {
			return Integer.parseInt(value.toString().trim()) > 0;
		} catch (NumberFormatException e) {
			return false;
		}
	}
}
