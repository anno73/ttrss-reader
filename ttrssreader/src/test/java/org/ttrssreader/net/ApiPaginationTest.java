package org.ttrssreader.net;

import android.util.Log;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.ttrssreader.controllers.Controller;
import org.ttrssreader.model.pojos.Article;
import org.ttrssreader.model.pojos.Feed;
import org.ttrssreader.preferences.Constants;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.SocketTimeoutException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ApiPaginationTest {
	private MockedStatic<Controller> controllers;
	private MockedStatic<Log> logs;
	private Controller controller;
	private JSONConnector connector;
	private final List<JsonObject> requests = new ArrayList<>();
	private final Deque<Object> responses = new ArrayDeque<>();

	@Before
	public void setUp() throws Exception {
		controllers = mockStatic(Controller.class);
		logs = mockStatic(Log.class);
		controller = mock(Controller.class);
		controllers.when(Controller::getInstance).thenReturn(controller);
		when(controller.hostname()).thenReturn("https://example.org/api/");
		when(controller.proxyHost()).thenReturn("");
		when(controller.apiTimeoutSeconds()).thenReturn(10);
		when(controller.lazyServerTimeoutMinutes()).thenReturn(10);
		when(controller.apiPageSize()).thenReturn(Constants.API_PAGE_SIZE_DEFAULT);
		connector = new JSONConnector();
		connector.pullLastError();
		field("sessionId", "test-session");
		field("apiLevel", 25);
		OkHttpClient client = mock(OkHttpClient.class);
		when(client.newCall(any(Request.class))).thenAnswer(invocation -> {
			Request request = invocation.getArgument(0);
			Buffer buffer = new Buffer();
			request.body().writeTo(buffer);
			requests.add(JsonParser.parseString(buffer.readUtf8()).getAsJsonObject());
			Call call = mock(Call.class);
			when(call.execute()).thenAnswer(execution -> {
				assertFalse("Unexpected extra API request: " + requests, responses.isEmpty());
				Object next = responses.remove();
				if (next instanceof IOException)
					throw (IOException) next;
				Reader reader = next instanceof Reader ? (Reader) next : new StringReader((String) next);
				Response response = mock(Response.class);
				ResponseBody body = mock(ResponseBody.class);
				when(response.isSuccessful()).thenReturn(true);
				when(response.code()).thenReturn(200);
				when(response.request()).thenReturn(request);
				when(response.body()).thenReturn(body);
				when(body.charStream()).thenReturn(reader);
				return response;
			});
			return call;
		});
		field("client", client);
	}

	@After
	public void tearDown() {
		logs.close();
		controllers.close();
	}

	@Test
	public void positiveIntegerValidationAndEndpointCaps() {
		for (String value : Arrays.asList("1", "200", "100000", "2147483647"))
			assertTrue(value, ApiPageSize.isValid(value));
		for (String value : Arrays.asList("", "0", "-1", "1.5", "abc", "2147483648", " 2 "))
			assertFalse(value, ApiPageSize.isValid(value));
		assertFalse(ApiPageSize.isValid(null));
		assertFalse(ApiPageSize.isValid(2));
		assertEquals(200, ApiPageSize.headlineLimit(Integer.MAX_VALUE, 25, Integer.MAX_VALUE));
		assertEquals(60, ApiPageSize.headlineLimit(200, 5, 1000));
		assertEquals(1, ApiPageSize.headlineLimit(1, 5, 1000));
		assertEquals(3, ApiPageSize.headlineLimit(200, 25, 3));
	}

	@Test
	public void headlinesDefaultAndLegacyLimitsAreSent() throws Exception {
		page();
		headlines(500);
		assertRequest(0, "getHeadlines", 200, "skip", 0);
		field("apiLevel", 5);
		page();
		headlines(500);
		assertRequest(1, "getHeadlines", 60, "skip", 0);
		when(controller.apiPageSize()).thenReturn(Integer.MAX_VALUE);
		field("apiLevel", 25);
		page();
		headlines(Integer.MAX_VALUE);
		assertRequest(2, "getHeadlines", 200, "skip", 0);
		assertFalse(connector.hasLastError());
	}

	@Test
	public void headlinesContinueWithRemainingBudget() {
		when(controller.apiPageSize()).thenReturn(2);
		page(article(1), article(2));
		page(article(3), article(4));
		page(article(5));
		Set<Article> articles = headlines(5);
		assertEquals(5, articles.size());
		assertRequest(0, "getHeadlines", 2, "skip", 0);
		assertRequest(1, "getHeadlines", 2, "skip", 2);
		assertRequest(2, "getHeadlines", 1, "skip", 4);
		assertEquals("42", requests.get(2).get("feed_id").getAsString());
		assertEquals("1", requests.get(2).get("is_cat").getAsString());
		assertEquals("7", requests.get(2).get("since_id").getAsString());
		assertEquals("release", requests.get(2).get("search").getAsString());
	}

	@Test
	public void headlineOffsetCountsOmittedAndDuplicateArticles() {
		when(controller.apiPageSize()).thenReturn(2);
		page(article(1), article(1));
		page(article(2), article(3));
		Set<Article> articles = new LinkedHashSet<>();
		IArticleOmitter filter = mock(IArticleOmitter.class);
		when(filter.omitArticle(eq(Article.ArticleField.id), any(Article.class))).thenAnswer(invocation ->
				((Article) invocation.getArgument(1)).id == 1);
		connector.getHeadlines(articles, 42, 2, "all_articles", true, 0, null, filter);
		assertEquals(2, articles.size());
		assertRequest(1, "getHeadlines", 2, "skip", 2);
		assertEquals(2, requests.size());
		assertTrue(responses.isEmpty());
	}

	@Test
	public void headlineDuplicateAcrossPagesDoesNotConsumeBudget() {
		when(controller.apiPageSize()).thenReturn(2);
		page(article(1), article(2));
		page(article(2), article(3));
		page(article(4));
		assertEquals(4, headlines(4).size());
		assertRequest(2, "getHeadlines", 1, "skip", 4);
	}

	@Test
	public void omittedPagesWithIdAfterFilterStillAdvance() {
		when(controller.apiPageSize()).thenReturn(1);
		page("{\"unread\":false,\"id\":1,\"title\":\"One\"}");
		page("{\"unread\":false,\"id\":2,\"title\":\"Two\"}");
		page(article(3));
		IArticleOmitter filter = mock(IArticleOmitter.class);
		when(filter.omitArticle(eq(Article.ArticleField.unread), any(Article.class))).thenReturn(true);
		Set<Article> articles = new LinkedHashSet<>();
		connector.getHeadlines(articles, 42, 1, "all_articles", true, 0, null, filter);
		assertEquals(1, articles.size());
		assertRequest(2, "getHeadlines", 1, "skip", 2);
		assertFalse(connector.hasLastError());
	}

	@Test
	public void headlineBudgetIncludesExistingContainerSizeWithoutOverflow() {
		Set<Article> articles = new LinkedHashSet<>();
		Article existing = new Article();
		existing.id = 10;
		articles.add(existing);
		page(article(1));
		connector.getHeadlines(articles, 42, Integer.MAX_VALUE, "unread", true, 0, null, null);
		assertEquals(2, articles.size());
		assertRequest(0, "getHeadlines", 200, "skip", 0);
	}

	@Test
	public void headlineExactFullPageChecksNextPageUnlessBudgetReached() {
		when(controller.apiPageSize()).thenReturn(1);
		page(article(1));
		page();
		assertEquals(1, headlines(3).size());
		assertRequest(1, "getHeadlines", 1, "skip", 1);
		assertFalse(connector.hasLastError());
	}

	@Test
	public void repeatedHeadlinePageTerminatesWithError() {
		when(controller.apiPageSize()).thenReturn(1);
		page(article(1));
		page(article(1));
		headlines(3);
		assertTrue(connector.hasLastError());
		assertTrue(connector.pullLastError().contains("no progress"));
		assertEquals(2, requests.size());
	}

	@Test
	public void feedPagesCountOnlyRealFeedsAndKeepMetadataOnce() {
		when(controller.apiPageSize()).thenReturn(2);
		page(feed(-11, -2, "Label"), feed(-4, -1, "All"), feed(-1, -1, "Ignored"),
				feed(1, 0, "One"), feed(2, 7, "Two"));
		page(feed(-11, -2, "Changed label"), feed(-4, -1, "Changed all"),
				feed(3, 0, "Three"), feed(4, 0, "Four"));
		page(feed(-11, -2, "Label"), feed(-4, -1, "All"), feed(5, 0, "Five"));
		List<Feed> result = new ArrayList<>(connector.getFeeds());
		assertEquals(Arrays.asList(-11, -4, 1, 2, 3, 4, 5),
				result.stream().map(feed -> feed.id).collect(Collectors.toList()));
		assertEquals("Label", result.get(0).title);
		assertEquals("All", result.get(1).title);
		assertEquals("https://example.org/feed/2", result.get(3).url);
		assertEquals(7, result.get(3).categoryId);
		assertEquals(3, result.get(3).unread);
		assertRequest(0, "getFeeds", 2, "offset", 0);
		assertRequest(1, "getFeeds", 2, "offset", 2);
		assertRequest(2, "getFeeds", 2, "offset", 4);
		assertEquals("-4", requests.get(0).get("cat_id").getAsString());
		assertFalse(connector.hasLastError());
	}

	@Test
	public void feedMetadataOnlyPageStopsDespiteExceedingLimit() {
		when(controller.apiPageSize()).thenReturn(1);
		page(feed(-11, -2, "A"), feed(-12, -2, "B"), feed(-4, -1, "All"));
		assertEquals(3, connector.getFeeds().size());
		assertEquals(1, requests.size());
	}

	@Test
	public void feedsDefaultLargeValuesAndChangesApplyNextDownload() {
		page();
		assertTrue(connector.getFeeds().isEmpty());
		assertRequest(0, "getFeeds", 200, "offset", 0);
		when(controller.apiPageSize()).thenReturn(Integer.MAX_VALUE);
		page(feed(1, 0, "One"));
		assertEquals(1, connector.getFeeds().size());
		assertRequest(1, "getFeeds", Integer.MAX_VALUE, "offset", 0);
		when(controller.apiPageSize()).thenReturn(1);
		page(feed(1, 0, "One"));
		page();
		assertEquals(1, connector.getFeeds().size());
		assertRequest(3, "getFeeds", 1, "offset", 1);
	}

	@Test
	public void feedsSnapshotSizeForAllPages() {
		when(controller.apiPageSize()).thenReturn(1, 10);
		page(feed(1, 0, "One"));
		page();
		connector.getFeeds();
		assertRequest(1, "getFeeds", 1, "offset", 1);
		verify(controller).apiPageSize();
	}

	@Test
	public void lazyServerFeedEnumerationUsesSamePagination() throws Exception {
		when(controller.apiPageSize()).thenReturn(1);
		page(feed(1, 0, "One"));
		page(feed(2, 0, "Two"));
		page();
		Method method = JSONConnector.class.getDeclaredMethod("getFeeds", boolean.class);
		method.setAccessible(true);
		assertEquals(2, ((Set<?>) method.invoke(connector, true)).size());
		assertRequest(2, "getFeeds", 1, "offset", 2);
	}

	@Test
	public void pageReadersCloseOnSuccessAndBeforeContentParseFailure() {
		StringReader success = spy(new StringReader("{\"content\":[]}"));
		responses.add(success);
		connector.getFeeds();
		verify(success).close();
		StringReader failure = spy(new StringReader("{\"content\":"));
		responses.add(failure);
		assertTrue(connector.getFeeds().isEmpty());
		verify(failure).close();
		assertTrue(connector.hasLastError());
	}

	@Test
	public void repeatedRealFeedPageFailsInsteadOfReturningPartialSuccess() {
		when(controller.apiPageSize()).thenReturn(1);
		page(feed(1, 0, "One"));
		page(feed(1, 0, "One"));
		assertTrue(connector.getFeeds().isEmpty());
		assertTrue(connector.pullLastError().contains("no progress"));
		assertEquals(2, requests.size());
	}

	@Test
	public void laterFeedApiErrorDoesNotReplaceCacheWithPartialList() {
		when(controller.apiPageSize()).thenReturn(1);
		page(feed(1, 0, "One"));
		responses.add("{\"status\":1,\"content\":{\"error\":\"E_OPERATION_FAILED\"}}");
		assertTrue(connector.getFeeds().isEmpty());
		assertTrue(connector.hasLastError());
		assertTrue(connector.pullLastError().contains("E_OPERATION_FAILED"));
		assertEquals(2, requests.size());
	}

	@Test
	public void expiredSessionIsNotAutomaticallyReplayed() {
		responses.add("{\"status\":1,\"content\":{\"error\":\"NOT_LOGGED_IN\"}}");
		headlines(5);
		assertTrue(connector.hasLastError());
		assertEquals("NOT_LOGGED_IN", connector.pullLastError());
		assertEquals(1, requests.size());
	}

	@Test
	public void laterFeedTransportTimeoutPreservesPageDiagnostics() {
		when(controller.apiPageSize()).thenReturn(1);
		page(feed(1, 0, "One"));
		responses.add(new SocketTimeoutException("timeout"));
		assertTrue(connector.getFeeds().isEmpty());
		assertTrue(connector.hasLastError());
		String error = connector.pullLastError();
		assertTrue(error.contains("\"offset\":\"1\""));
		assertTrue(error.contains("\"limit\":\"1\""));
		assertEquals(2, requests.size());
	}

	@Test
	public void malformedFeedPageStopsAndSurfacesError() {
		when(controller.apiPageSize()).thenReturn(1);
		page(feed(1, 0, "One"));
		responses.add("{\"content\":[{\"id\":2,");
		assertTrue(connector.getFeeds().isEmpty());
		assertTrue(connector.hasLastError());
		assertEquals(2, requests.size());
	}

	@Test
	public void invalidFeedRecordSurfacesError() {
		page("{\"title\":\"Missing id\"}");
		assertTrue(connector.getFeeds().isEmpty());
		assertTrue(connector.hasLastError());
	}

	@Test
	public void malformedHeadlinePageStopsAndSurfacesError() {
		when(controller.apiPageSize()).thenReturn(1);
		page(article(1));
		responses.add("{\"content\":[{\"id\":2,");
		headlines(3);
		assertTrue(connector.hasLastError());
		assertEquals(2, requests.size());
	}

	@Test
	public void headlineBodyTimeoutStopsWithoutRetryAndPreservesDiagnostics() {
		responses.add(new Reader() {
			@Override
			public int read(char[] buffer, int offset, int length) throws IOException {
				throw new SocketTimeoutException("Read timed out");
			}
			@Override
			public void close() {
			}
		});
		headlines(5);
		assertTrue(connector.hasLastError());
		assertTrue(connector.pullLastError().contains("\"op\":\"getHeadlines\""));
		assertEquals(1, requests.size());
	}

	@Test
	public void categoriesRemainUnpaged() {
		page();
		assertTrue(connector.getCategories().isEmpty());
		assertEquals("getCategories", requests.get(0).get("op").getAsString());
		assertFalse(requests.get(0).has("limit"));
		assertFalse(requests.get(0).has("offset"));
	}

	private Set<Article> headlines(int total) {
		Set<Article> articles = new LinkedHashSet<>();
		connector.getHeadlines(articles, 42, total, "unread", true, 7, "release", null);
		return articles;
	}

	private void page(String... records) {
		responses.add("{\"status\":0,\"content\":[" + String.join(",", records) + "]}");
	}

	private static String article(int id) {
		return "{\"id\":" + id + ",\"title\":\"Article " + id + "\"}";
	}

	private static String feed(int id, int category, String title) {
		return "{\"id\":" + id + ",\"cat_id\":" + category + ",\"title\":\"" + title
				+ "\",\"feed_url\":\"https://example.org/feed/" + id + "\",\"unread\":3}";
	}

	private void assertRequest(int index, String operation, int limit, String offsetKey, int offset) {
		JsonObject request = requests.get(index);
		assertEquals(operation, request.get("op").getAsString());
		assertEquals(limit, request.get("limit").getAsInt());
		assertEquals(offset, request.get(offsetKey).getAsInt());
	}

	private void field(String name, Object value) throws Exception {
		Field field = JSONConnector.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(connector, value);
	}
}
