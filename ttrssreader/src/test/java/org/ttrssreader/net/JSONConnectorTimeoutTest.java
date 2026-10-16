package org.ttrssreader.net;

import android.util.Log;

import com.google.gson.JsonParser;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.ttrssreader.controllers.Controller;
import org.ttrssreader.controllers.DBHelper;
import org.ttrssreader.model.pojos.Feed;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class JSONConnectorTimeoutTest {
	private MockedStatic<Controller> controllerStatic;
	private MockedStatic<Log> logs;
	private Controller controller;
	private JSONConnector connector;
	private OkHttpClient client;
	private Call call;

	@Before
	public void setUp() throws Exception {
		controllerStatic = mockStatic(Controller.class);
		logs = mockStatic(Log.class);
		controller = mock(Controller.class);
		controllerStatic.when(Controller::getInstance).thenReturn(controller);
		when(controller.hostname()).thenReturn("https://example.org/api/index.php?token=endpoint-secret");
		when(controller.proxyHost()).thenReturn("");
		connector = new JSONConnector();
		connector.pullLastError();
		setField("sessionId", "session-secret");
		client = mock(OkHttpClient.class);
		call = mock(Call.class);
		when(client.newCall(any(Request.class))).thenReturn(call);
		setField("client", client);
	}

	@After
	public void tearDown() {
		if (connector != null)
			connector.pullLastError();
		if (logs != null)
			logs.close();
		if (controllerStatic != null)
			controllerStatic.close();
	}

	@Test
	public void executionTimeoutPreservesErrorThroughNoAnswerWorkaround() throws Exception {
		when(call.execute()).thenThrow(new SocketTimeoutException("Read timed out"));
		Map<String, String> params = new HashMap<>();
		params.put("op", "updateFeed");
		params.put("feed_id", "12");
		assertFalse(noAnswer(params));
		assertTrue(connector.hasLastError());
		String message = connector.pullLastError();
		assertTrue(message.contains("URL: https://example.org/api/index.php?token=VALUE_REDACTED"));
		assertTrue(message.contains("\"op\":\"updateFeed\""));
		assertTrue(message.contains("\"feed_id\":\"12\""));
		assertTrue(message.contains("\"sid\":\"VALUE_REDACTED\""));
		assertFalse(connector.hasLastError());
		assertLogged(message);
		assertOriginalBody("updateFeed");
	}

	@Test
	public void bodyReadTimeoutUsesCapturedEndpointAndSurvivesNoAnswerWorkaround() throws Exception {
		try (MockedStatic<DBHelper> dbHelperStatic = mockStatic(DBHelper.class)) {
			DBHelper dbHelper = mock(DBHelper.class);
			Feed feed = new Feed();
			feed.id = 12;
			feed.title = "Daily News";
			feed.url = "https://feed-user:feed-pass@feeds.example.org/rss?token=feed-secret&format=xml";
			dbHelperStatic.when(DBHelper::getInstance).thenReturn(dbHelper);
			when(dbHelper.getFeed(12)).thenReturn(feed);

			Response response = response(new Reader() {
				@Override
				public int read(char[] buffer, int offset, int length) throws IOException {
					when(controller.hostname()).thenReturn("https://changed.example.org/api/");
					throw new SocketTimeoutException("session-secret endpoint-secret feed-secret");
				}
				@Override
				public void close() {
				}
			});
			when(call.execute()).thenReturn(response);
			Map<String, String> params = new HashMap<>();
			params.put("op", "updateFeed");
			params.put("feed_id", "12");
			assertFalse(noAnswer(params));
			assertTrue(connector.hasLastError());
			String message = connector.pullLastError();
			assertTrue(message.contains("URL: https://example.org/api/index.php?token=VALUE_REDACTED"));
			assertTrue(message.contains("Feed name: Daily News"));
			assertTrue(message.contains("Feed URL: https://VALUE_REDACTED:VALUE_REDACTED@feeds.example.org/rss"
					+ "?token=VALUE_REDACTED&format=xml"));
			assertFalse(message.contains("changed.example.org"));
			assertFalse(message.contains("session-secret"));
			assertFalse(message.contains("endpoint-secret"));
			assertFalse(message.contains("feed-secret"));
			assertLogged(message);
		}
	}

	@Test
	public void feedTimeoutsResolveLocalMetadataButCategoryRequestsDoNot() throws Exception {
		try (MockedStatic<DBHelper> dbHelperStatic = mockStatic(DBHelper.class)) {
			DBHelper dbHelper = mock(DBHelper.class);
			Feed feed = new Feed();
			feed.id = 12;
			feed.title = "Daily News";
			feed.url = "https://feeds.example.org/rss";
			dbHelperStatic.when(DBHelper::getInstance).thenReturn(dbHelper);
			when(dbHelper.getFeed(12)).thenReturn(feed);
			when(call.execute()).thenThrow(new SocketTimeoutException("timeout"));

			String[][] operations = {
					{"updateFeed"},
					{"getHeadlines", "0"},
					{"catchupFeed", "0"},
					{"unsubscribeFeed"}
			};
			for (String[] operation : operations) {
				Map<String, String> params = new HashMap<>();
				params.put("op", operation[0]);
				params.put("feed_id", "12");
				if (operation.length > 1)
					params.put("is_cat", operation[1]);
				assertFalse(noAnswer(params));
				String message = connector.pullLastError();
				assertTrue(operation[0], message.contains("Feed name: Daily News"));
				assertTrue(operation[0], message.contains("Feed URL: https://feeds.example.org/rss"));
			}

			Map<String, String> categoryParams = new HashMap<>();
			for (String operation : new String[]{"getHeadlines", "catchupFeed"}) {
				categoryParams.put("op", operation);
				categoryParams.put("feed_id", "12");
				categoryParams.put("is_cat", "1");
				assertFalse(noAnswer(categoryParams));
				String categoryMessage = connector.pullLastError();
				assertFalse(categoryMessage.contains("Feed name:"));
				assertFalse(categoryMessage.contains("Feed URL:"));
			}
			when(dbHelper.getFeed(99)).thenReturn(new Feed());
			Map<String, String> missingFeedParams = new HashMap<>();
			missingFeedParams.put("op", "getHeadlines");
			missingFeedParams.put("feed_id", "99");
			missingFeedParams.put("is_cat", "0");
			assertFalse(noAnswer(missingFeedParams));
			String missingFeedMessage = connector.pullLastError();
			assertFalse(missingFeedMessage.contains("Feed name:"));
			assertFalse(missingFeedMessage.contains("Feed URL:"));
			verify(dbHelper, times(4)).getFeed(12);
		}
	}

	@Test
	public void getPrefTimeoutIsAvailableToExistingErrorConsumers() throws Exception {
		when(call.execute()).thenThrow(new SocketTimeoutException("timeout"));
		assertNull(connector.getPref("DEFAULT_UPDATE_INTERVAL"));
		assertTrue(connector.hasLastError());
		String message = connector.pullLastError();
		assertTrue(message.contains("\"pref_name\":\"DEFAULT_UPDATE_INTERVAL\""));
		assertFalse(message.contains("Feed name:"));
		assertLogged(message);
		assertOriginalBody("getPref");
	}

	@Test
	public void nonTimeoutExecutionErrorsKeepExistingText() throws Exception {
		when(call.execute()).thenThrow(new IOException("Canceled"));
		assertNull(connector.getPref("DEFAULT_UPDATE_INTERVAL"));
		assertTrue(connector.hasLastError());
		assertEquals("Exception in doRequest(): Exception-Message: Canceled No Exception-Cause available.",
				connector.pullLastError());
	}

	@Test
	public void emptyResponseKeepsExistingEofErrorBehavior() throws Exception {
		Response response = response(new java.io.StringReader(""));
		when(call.execute()).thenReturn(response);
		Map<String, String> params = new HashMap<>();
		params.put("op", "updateFeed");
		params.put("feed_id", "12");
		assertFalse(noAnswer(params));
		assertTrue(connector.hasLastError());
		assertTrue(connector.pullLastError().contains("End of input"));
	}

	@Test
	public void emptyJsonResultStillReturnsFalseWithoutError() throws Exception {
		Response response = response(new java.io.StringReader("{}"));
		when(call.execute()).thenReturn(response);
		Map<String, String> params = new HashMap<>();
		params.put("op", "updateFeed");
		params.put("feed_id", "12");
		assertFalse(noAnswer(params));
		assertFalse(connector.hasLastError());
	}

	@Test
	public void malformedUpdateFeedResponseStillUsesExistingWorkaround() throws Exception {
		Response response = response(new java.io.StringReader("not-json"));
		when(call.execute()).thenReturn(response);
		Map<String, String> params = new HashMap<>();
		params.put("op", "updateFeed");
		params.put("feed_id", "12");
		assertFalse(noAnswer(params));
		assertFalse(connector.hasLastError());
	}

	@Test
	public void longTimeoutJsonIsFullyLoggedWithoutTruncation() throws Exception {
		when(call.execute()).thenThrow(new SocketTimeoutException("timeout"));
		Map<String, String> params = new HashMap<>();
		params.put("op", "shareToPublished");
		params.put("title", "title");
		params.put("url", "https://example.org/article");
		params.put("content", "article-content \uD83D\uDE00\u4E2D".repeat(1000));
		assertFalse(noAnswer(params));
		String message = connector.pullLastError();
		ArgumentCaptor<String> parts = ArgumentCaptor.forClass(String.class);
		logs.verify(() -> Log.e(eq("JSONConnector"), parts.capture()), atLeastOnce());
		String output = String.join("", parts.getAllValues());
		assertTrue(output.startsWith(message));
		assertTrue(message.contains(params.get("content")));
		for (String part : parts.getAllValues()) {
			assertTrue(part.length() <= 1000);
			assertTrue(part.getBytes(StandardCharsets.UTF_8).length <= 3000);
			assertFalse(Character.isHighSurrogate(part.charAt(part.length() - 1)));
		}
	}

	private Response response(Reader reader) {
		Response response = mock(Response.class);
		ResponseBody body = mock(ResponseBody.class);
		when(response.isSuccessful()).thenReturn(true);
		when(response.code()).thenReturn(200);
		when(response.body()).thenReturn(body);
		when(response.request()).thenReturn(new Request.Builder()
				.url("https://example.org/api/index.php?token=endpoint-secret").build());
		when(body.charStream()).thenReturn(reader);
		return response;
	}

	private boolean noAnswer(Map<String, String> params) throws Exception {
		Method method = JSONConnector.class.getDeclaredMethod("doRequestNoAnswer", Map.class);
		method.setAccessible(true);
		return (Boolean) method.invoke(connector, params);
	}

	private void setField(String name, Object value) throws Exception {
		Field field = JSONConnector.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(connector, value);
	}

	private void assertLogged(String message) {
		logs.verify(() -> Log.e("JSONConnector", message));
	}

	private void assertOriginalBody(String operation) throws IOException {
		ArgumentCaptor<Request> request = ArgumentCaptor.forClass(Request.class);
		verify(client).newCall(request.capture());
		Buffer buffer = new Buffer();
		request.getValue().body().writeTo(buffer);
		com.google.gson.JsonObject json = JsonParser.parseString(buffer.readUtf8()).getAsJsonObject();
		assertEquals(operation, json.get("op").getAsString());
		assertEquals("session-secret", json.get("sid").getAsString());
	}
}
