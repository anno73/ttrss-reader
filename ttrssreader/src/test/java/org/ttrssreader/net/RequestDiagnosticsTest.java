package org.ttrssreader.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.HttpUrl;

import static org.junit.Assert.*;

public class RequestDiagnosticsTest {
	private static final HttpUrl ENDPOINT = HttpUrl.get("https://example.org:8443/tt-rss/api/index.php");

	@Test
	public void retainsEveryActiveOperationAndVariantExceptCredentials() {
		String[] requests = {
				"{\"op\":\"login\",\"user\":\"alice\",\"password\":\"login-secret\"}",
				"{\"op\":\"getCategories\"}",
				"{\"op\":\"getFeeds\",\"cat_id\":\"-4\"}",
				"{\"op\":\"getHeadlines\",\"feed_id\":\"12\",\"limit\":\"200\",\"skip\":\"0\","
						+ "\"view_mode\":\"unread\",\"is_cat\":\"0\",\"show_content\":\"1\","
						+ "\"include_attachments\":\"1\",\"since_id\":\"5\",\"search\":\"private search\"}",
				"{\"op\":\"updateFeed\",\"feed_id\":\"12\"}",
				"{\"op\":\"updateArticle\",\"article_ids\":\"1,2\",\"mode\":\"1\",\"field\":\"2\"}",
				"{\"op\":\"updateArticle\",\"article_ids\":\"1,2\",\"mode\":\"1\",\"field\":\"0\"}",
				"{\"op\":\"updateArticle\",\"article_ids\":\"1,2\",\"mode\":\"1\",\"field\":\"1\"}",
				"{\"op\":\"updateArticle\",\"article_ids\":\"1\",\"field\":\"3\",\"data\":\"private note\"}",
				"{\"op\":\"catchupFeed\",\"feed_id\":\"12\",\"is_cat\":\"0\",\"mode\":\"all\"}",
				"{\"op\":\"unsubscribeFeed\",\"feed_id\":\"12\"}",
				"{\"op\":\"getPref\",\"pref_name\":\"DEFAULT_UPDATE_INTERVAL\"}",
				"{\"op\":\"setArticleLabel\",\"article_ids\":\"1,2\",\"label_id\":\"3\",\"assign\":\"1\"}",
				"{\"op\":\"shareToPublished\",\"title\":\"private title\",\"content\":\"<p>private content</p>\","
						+ "\"url\":\"https://example.org?article=3\"}",
				"{\"op\":\"subscribeToFeed\",\"feed_url\":\"https://example.org?feed=3\",\"category_id\":\"2\"}"
		};
		for (String request : requests) {
			JsonObject original = JsonParser.parseString(request).getAsJsonObject();
			original.addProperty("sid", "session-secret");
			String before = original.toString();
			JsonObject expected = original.deepCopy();
			expected.addProperty("sid", RequestDiagnostics.REDACTED);
			if (expected.has("user")) {
				expected.addProperty("user", RequestDiagnostics.REDACTED);
				expected.addProperty("password", RequestDiagnostics.REDACTED);
			}
			RequestDiagnostics diagnostics = new RequestDiagnostics(ENDPOINT, before);
			assertEquals(request, expected, JsonParser.parseString(diagnostics.json()));
			assertEquals(before, original.toString());
			String message = diagnostics.message(new SocketTimeoutException("Read timed out"));
			assertTrue(message.contains("URL: " + ENDPOINT));
			assertTrue(message.contains("JSON: " + diagnostics.json()));
			assertFalse(message.contains("session-secret"));
		}
	}

	@Test
	public void absentOptionalFieldsAndSingleUserLoginStayAbsent() {
		assertEquals("{\"op\":\"login\"}", new RequestDiagnostics(ENDPOINT, "{\"op\":\"login\"}").json());
		assertEquals("{\"op\":\"getHeadlines\",\"feed_id\":\"1\"}",
				new RequestDiagnostics(ENDPOINT, "{\"op\":\"getHeadlines\",\"feed_id\":\"1\"}").json());
	}

	@Test
	public void redactsEndpointCredentialsAndRepeatedEncodedQueryValues() {
		HttpUrl url = HttpUrl.get("https://alice:p%40ss@example.org:8443/tt-rss/api/index.php"
				+ "?API-Token=secret%2Bvalue&sid=first&sid=second&user=alice&search=ordinary%20text&empty=&flag#section");
		RequestDiagnostics diagnostics = new RequestDiagnostics(url, "{\"op\":\"getCategories\"}");
		String message = diagnostics.message(new SocketTimeoutException("timeout"));
		assertTrue(message.contains("URL: https://VALUE_REDACTED:VALUE_REDACTED@example.org:8443/tt-rss/api/index.php"
				+ "?API-Token=VALUE_REDACTED&sid=VALUE_REDACTED&sid=VALUE_REDACTED&user=VALUE_REDACTED"
				+ "&search=ordinary%20text&empty=&flag#section"));
		assertEquals("alice", url.username());
		assertEquals("p@ss", url.password());
		assertEquals("secret+value", url.queryParameter("API-Token"));
	}

	@Test
	public void redactsAllKnownCredentialAliasesWithoutRedactingOperation() {
		String[] names = {"user", "username", "login", "password", "passwd", "pwd", "token", "api_token",
				"api_key", "apikey", "access_token", "refresh_token", "auth", "authorization",
				"sid", "session_id", "secret", "client_secret"};
		JsonObject json = new JsonObject();
		json.addProperty("op", "login");
		HttpUrl.Builder endpoint = ENDPOINT.newBuilder();
		for (String name : names) {
			json.addProperty(name.toUpperCase().replace('_', '-'), "credential-" + name);
			endpoint.addQueryParameter(name, "credential-" + name);
		}
		RequestDiagnostics diagnostics = new RequestDiagnostics(endpoint.build(), json.toString());
		JsonObject sanitized = JsonParser.parseString(diagnostics.json()).getAsJsonObject();
		assertEquals("login", sanitized.get("op").getAsString());
		for (String name : names) {
			assertEquals(RequestDiagnostics.REDACTED, sanitized.get(name.toUpperCase().replace('_', '-')).getAsString());
			assertFalse(diagnostics.message(new SocketTimeoutException()).contains("credential-" + name));
		}
	}

	@Test
	public void sanitizesBodyUrlsAndOptionalSubscriptionCredentialsButKeepsFreeText() {
		String body = "{\"op\":\"subscribeToFeed\",\"login\":\"feed-user\",\"password\":\"feed-pass\","
				+ "\"feed_url\":\"https://feed-user:feed-pass@example.org/rss?token=feed-secret&format=xml\","
				+ "\"url\":\"https://example.org/article?sid=article-secret&id=3\","
				+ "\"search\":\"password=visible\",\"data\":\"token=visible\","
				+ "\"title\":\"private title\",\"content\":\"<p>private content</p>\"}";
		JsonObject result = JsonParser.parseString(new RequestDiagnostics(ENDPOINT, body).json()).getAsJsonObject();
		assertEquals(RequestDiagnostics.REDACTED, result.get("login").getAsString());
		assertEquals(RequestDiagnostics.REDACTED, result.get("password").getAsString());
		assertEquals("https://VALUE_REDACTED:VALUE_REDACTED@example.org/rss?token=VALUE_REDACTED&format=xml",
				result.get("feed_url").getAsString());
		assertEquals("https://example.org/article?sid=VALUE_REDACTED&id=3", result.get("url").getAsString());
		for (String name : new String[]{"search", "data", "title", "content"})
			assertEquals(JsonParser.parseString(body).getAsJsonObject().get(name), result.get(name));
	}

	@Test
	public void exceptionMessagesCausesAndStacksCannotReintroduceKnownSecrets() {
		RequestDiagnostics diagnostics = new RequestDiagnostics(
				HttpUrl.get("https://user-name:encoded%2Bsecret@example.org/api?token=query-secret"),
				"{\"password\":\"body-secret\",\"sid\":\"session-secret\"}");
		diagnostics.redactCredential("Basic header-secret");
		SocketTimeoutException exception = new SocketTimeoutException(
				"user-name encoded%2Bsecret body-secret Basic header-secret");
		exception.initCause(new IOException("encoded+secret query-secret session-secret"));
		String output = diagnostics.message(exception) + diagnostics.stackTrace(exception);
		for (String secret : new String[]{"user-name", "encoded%2Bsecret", "encoded+secret", "body-secret",
				"header-secret", "query-secret", "session-secret"})
			assertFalse(secret, output.contains(secret));
		assertTrue(output.contains("SocketTimeoutException"));
		assertTrue(output.contains("IOException"));
		assertTrue(output.contains("RequestDiagnosticsTest"));
	}

	@Test
	public void replacementDoesNotRedactItsOwnPlaceholderOrPartialLongerSecret() {
		RequestDiagnostics diagnostics = new RequestDiagnostics(ENDPOINT,
				"{\"password\":\"VALUE\",\"sid\":\"VALUE_LONG\"}");
		String message = diagnostics.message(new SocketTimeoutException("VALUE_LONG VALUE"));
		assertTrue(message.contains("SocketTimeoutException: VALUE_REDACTED VALUE_REDACTED"));
	}

	@Test
	public void recognizesNetworkTimeoutsButNotCancellationOrOtherFailures() {
		assertTrue(RequestDiagnostics.isTimeout(new SocketTimeoutException()));
		assertTrue(RequestDiagnostics.isTimeout(new InterruptedIOException("timeout")));
		assertTrue(RequestDiagnostics.isTimeout(new IOException("wrapped", new SocketTimeoutException())));
		assertFalse(RequestDiagnostics.isTimeout(new InterruptedIOException("interrupted")));
		assertFalse(RequestDiagnostics.isTimeout(new IOException("Canceled")));
		assertFalse(RequestDiagnostics.isTimeout(new IOException("unrelated timeout text")));
		assertFalse(RequestDiagnostics.isTimeout(new IllegalStateException("timeout")));
	}

	@Test
	public void delayedReadsReportOnceAndPreserveOriginalExceptionAndClose() throws IOException {
		SocketTimeoutException failure = new SocketTimeoutException("timeout");
		AtomicInteger closes = new AtomicInteger();
		List<String> reports = new ArrayList<>();
		RequestDiagnostics diagnostics = new RequestDiagnostics(ENDPOINT, "{\"op\":\"getHeadlines\",\"sid\":\"secret\"}");
		Reader reader = diagnostics.monitor(failingReader(failure, closes), (context, e) -> reports.add(context.message(e)));
		assertSame(failure, assertThrows(SocketTimeoutException.class, () -> reader.read(new char[4])));
		assertSame(failure, assertThrows(SocketTimeoutException.class, reader::read));
		reader.close();
		assertEquals(1, reports.size());
		assertEquals(1, closes.get());
		assertTrue(reports.get(0).contains("URL: " + ENDPOINT));
		assertFalse(reports.get(0).contains("\"secret\""));
	}

	@Test
	public void responseEndpointRetainsOriginalBodyAndOriginalSecretRedaction() {
		RequestDiagnostics original = new RequestDiagnostics(
				HttpUrl.get("https://example.org/api?token=initial-secret"), "{\"op\":\"login\",\"password\":\"body-secret\"}");
		RequestDiagnostics redirected = original.withUrl(HttpUrl.get("https://other.example.org/api?sid=redirect-secret"));
		String message = redirected.message(new SocketTimeoutException("initial-secret body-secret redirect-secret"));
		assertTrue(message.contains("URL: https://other.example.org/api?sid=VALUE_REDACTED"));
		assertTrue(message.contains("\"op\":\"login\""));
		assertFalse(message.contains("initial-secret"));
		assertFalse(message.contains("body-secret"));
		assertFalse(message.contains("redirect-secret"));
	}

	@Test
	public void ordinaryReadsAndNonTimeoutFailuresAreUnchanged() throws IOException {
		AtomicInteger reports = new AtomicInteger();
		RequestDiagnostics diagnostics = new RequestDiagnostics(ENDPOINT, "{}");
		try (Reader reader = diagnostics.monitor(new StringReader("abc"), (context, e) -> reports.incrementAndGet())) {
			assertEquals('a', reader.read());
			char[] remainder = new char[2];
			assertEquals(2, reader.read(remainder));
			assertArrayEquals(new char[]{'b', 'c'}, remainder);
			assertEquals(-1, reader.read());
		}
		for (IOException failure : new IOException[]{new IOException("Canceled"), new InterruptedIOException("interrupted")}) {
			try (Reader reader = diagnostics.monitor(failingReader(failure, new AtomicInteger()),
					(context, e) -> reports.incrementAndGet())) {
				assertSame(failure, assertThrows(IOException.class, reader::read));
			}
		}
		assertEquals(0, reports.get());
	}

	@Test
	public void closeTimeoutAlsoReportsOriginalFailure() {
		SocketTimeoutException failure = new SocketTimeoutException("timeout");
		AtomicInteger reports = new AtomicInteger();
		Reader closing = new RequestDiagnostics(ENDPOINT, "{}").monitor(new Reader() {
			@Override
			public int read(char[] buffer, int offset, int length) {
				return -1;
			}
			@Override
			public void close() throws IOException {
				throw failure;
			}
		}, (context, e) -> reports.incrementAndGet());
		assertSame(failure, assertThrows(SocketTimeoutException.class, closing::close));
		assertEquals(1, reports.get());
	}

	private static Reader failingReader(IOException failure, AtomicInteger closes) {
		return new Reader() {
			@Override
			public int read(char[] buffer, int offset, int length) throws IOException {
				throw failure;
			}
			@Override
			public void close() {
				closes.incrementAndGet();
			}
		};
	}
}
