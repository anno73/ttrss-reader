package org.ttrssreader.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.io.FilterReader;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;

final class RequestDiagnostics {
	static final String REDACTED = "VALUE_REDACTED";
	private static final Set<String> CREDENTIAL_PARAMETERS = new HashSet<>(Arrays.asList(
			"user", "username", "login", "password", "passwd", "pwd", "token", "api_token",
			"api_key", "apikey", "access_token", "refresh_token", "auth", "authorization",
			"sid", "session_id", "secret", "client_secret"));

	private final Set<String> secrets = new TreeSet<>(
			Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder()));
	private final String url;
	private final String json;

	RequestDiagnostics(HttpUrl requestUrl, String requestJson) {
		url = sanitizeUrl(requestUrl);
		JsonObject body = JsonParser.parseString(requestJson).getAsJsonObject();
		for (Map.Entry<String, JsonElement> entry : body.entrySet()) {
			String name = entry.getKey();
			if (isCredential(name)) {
				remember(entry.getValue().getAsString());
				entry.setValue(new JsonPrimitive(REDACTED));
			} else if ("url".equals(name) || "feed_url".equals(name)) {
				HttpUrl nestedUrl = HttpUrl.parse(entry.getValue().getAsString());
				if (nestedUrl != null) {
					String sanitized = sanitizeUrl(nestedUrl);
					if (!sanitized.equals(nestedUrl.toString()))
						entry.setValue(new JsonPrimitive(sanitized));
				}
			}
		}
		json = body.toString();
	}

	private RequestDiagnostics(RequestDiagnostics original, HttpUrl responseUrl) {
		secrets.addAll(original.secrets);
		url = sanitizeUrl(responseUrl);
		json = original.json;
	}

	RequestDiagnostics withUrl(HttpUrl responseUrl) {
		return new RequestDiagnostics(this, responseUrl);
	}

	String json() {
		return json;
	}

	void redactCredential(String value) {
		remember(value);
	}

	private static boolean isCredential(String name) {
		return CREDENTIAL_PARAMETERS.contains(name.toLowerCase(Locale.ROOT).replace('-', '_'));
	}

	private String sanitizeUrl(HttpUrl original) {
		HttpUrl.Builder builder = original.newBuilder();
		if (!original.username().isEmpty()) {
			remember(original.username());
			remember(original.encodedUsername());
			builder.username(REDACTED);
		}
		if (!original.password().isEmpty()) {
			remember(original.password());
			remember(original.encodedPassword());
			builder.password(REDACTED);
		}
		// Rebuild the query in order so repeated parameters are preserved.
		if (original.encodedQuery() != null) {
			builder.query(null);
			String[] encodedParameters = original.encodedQuery().split("&", -1);
			for (int i = 0; i < original.querySize(); i++) {
				String name = original.queryParameterName(i);
				String encodedParameter = encodedParameters[i];
				int separator = encodedParameter.indexOf('=');
				if (isCredential(name)) {
					remember(original.queryParameterValue(i));
					if (separator >= 0)
						remember(encodedParameter.substring(separator + 1));
					builder.addEncodedQueryParameter(separator >= 0
							? encodedParameter.substring(0, separator) : encodedParameter, REDACTED);
				} else {
					builder.addEncodedQueryParameter(separator >= 0
							? encodedParameter.substring(0, separator) : encodedParameter,
							separator >= 0 ? encodedParameter.substring(separator + 1) : null);
				}
			}
		}
		return builder.build().toString();
	}

	private void remember(String value) {
		if (value != null && !value.isEmpty()) {
			secrets.add(value);
			// Exception text may contain JSON-escaped credential values.
			String quoted = new JsonPrimitive(value).toString();
			secrets.add(quoted.substring(1, quoted.length() - 1));
		}
	}

	private String sanitizeExceptionText(String text) {
		if (secrets.isEmpty())
			return text;
		StringBuilder alternatives = new StringBuilder();
		for (String secret : secrets)
			alternatives.append(alternatives.length() == 0 ? "" : "|").append(Pattern.quote(secret));
		return Pattern.compile(alternatives.toString()).matcher(text)
				.replaceAll(Matcher.quoteReplacement(REDACTED));
	}

	String message(IOException exception) {
		String details = exception.getClass().getSimpleName() + ": " + exception.getMessage();
		if (exception.getCause() != null)
			details += " (Cause: " + exception.getCause() + ")";
		return "Timeout in API request: " + sanitizeExceptionText(details)
				+ "\nURL: " + url + "\nJSON: " + json;
	}

	String stackTrace(IOException exception) {
		StringWriter trace = new StringWriter();
		exception.printStackTrace(new PrintWriter(trace));
		return sanitizeExceptionText(trace.toString());
	}

	static boolean isTimeout(Throwable exception) {
		Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		while (exception != null && seen.add(exception)) {
			if (exception instanceof SocketTimeoutException)
				return true;
			if (exception instanceof InterruptedIOException) {
				String message = exception.getMessage();
				if ("timeout".equalsIgnoreCase(message) || "timed out".equalsIgnoreCase(message))
					return true;
			}
			exception = exception.getCause();
		}
		return false;
	}

	Reader monitor(Reader reader, BiConsumer<RequestDiagnostics, IOException> report) {
		return new FilterReader(reader) {
			private boolean reported;

			private IOException failed(IOException exception) {
				if (!reported && isTimeout(exception)) {
					reported = true;
					report.accept(RequestDiagnostics.this, exception);
				}
				return exception;
			}

			@Override
			public int read() throws IOException {
				try {
					return super.read();
				} catch (IOException e) {
					throw failed(e);
				}
			}

			@Override
			public int read(char[] buffer, int offset, int length) throws IOException {
				try {
					return super.read(buffer, offset, length);
				} catch (IOException e) {
					throw failed(e);
				}
			}

			@Override
			public void close() throws IOException {
				try {
					super.close();
				} catch (IOException e) {
					throw failed(e);
				}
			}
		};
	}
}
