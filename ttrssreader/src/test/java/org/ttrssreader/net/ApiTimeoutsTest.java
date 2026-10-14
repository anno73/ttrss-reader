package org.ttrssreader.net;

import android.util.Log;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.ttrssreader.controllers.Controller;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import okhttp3.OkHttpClient;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ApiTimeoutsTest {
	private MockedStatic<Controller> controllerStatic;
	private MockedStatic<Log> logs;
	private Controller controller;

	@Before
	public void setUp() {
		controllerStatic = mockStatic(Controller.class);
		logs = mockStatic(Log.class);
		controller = mock(Controller.class);
		controllerStatic.when(Controller::getInstance).thenReturn(controller);
		when(controller.proxyHost()).thenReturn("");
		when(controller.apiTimeoutSeconds()).thenReturn(10);
		when(controller.lazyServerTimeoutMinutes()).thenReturn(10);
	}

	@After
	public void tearDown() {
		logs.close();
		controllerStatic.close();
	}

	@Test
	public void regularTimeoutIsInSeconds() {
		assertEquals(10, ApiTimeouts.effectiveTimeoutSeconds(false, 10, 10));
		assertEquals(42, ApiTimeouts.effectiveTimeoutSeconds(false, 42, 10));
	}

	@Test
	public void lazyTimeoutIsInMinutes() {
		assertEquals(600, ApiTimeouts.effectiveTimeoutSeconds(true, 10, 10));
		assertEquals(180, ApiTimeouts.effectiveTimeoutSeconds(true, 10, 3));
	}

	@Test
	public void timeoutIsClampedToOkHttpLimits() {
		assertEquals(1, ApiTimeouts.effectiveTimeoutSeconds(false, 0, 10));
		assertEquals(ApiTimeouts.MAX_TIMEOUT_SECONDS, ApiTimeouts.effectiveTimeoutSeconds(false, Integer.MAX_VALUE, 10));
		assertEquals(ApiTimeouts.MAX_TIMEOUT_SECONDS, ApiTimeouts.effectiveTimeoutSeconds(true, 10, Integer.MAX_VALUE));
		new OkHttpClient.Builder().readTimeout(ApiTimeouts.MAX_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS).build();
	}

	@Test
	public void onlyPositiveIntegersAreValid() {
		assertTrue(ApiTimeouts.isValidTimeout("1"));
		assertTrue(ApiTimeouts.isValidTimeout("100000"));
		assertFalse(ApiTimeouts.isValidTimeout("0"));
		assertFalse(ApiTimeouts.isValidTimeout("-5"));
		assertFalse(ApiTimeouts.isValidTimeout(""));
		assertFalse(ApiTimeouts.isValidTimeout("abc"));
		assertFalse(ApiTimeouts.isValidTimeout(null));
	}

	@Test
	public void connectorUsesDefaultTimeoutForAllPhases() throws Exception {
		OkHttpClient client = client(new JSONConnector());
		assertEquals(10_000, client.connectTimeoutMillis());
		assertEquals(10_000, client.readTimeoutMillis());
		assertEquals(10_000, client.writeTimeoutMillis());
	}

	@Test
	public void connectorRebuildsClientWhenTimeoutSettingsChange() throws Exception {
		JSONConnector connector = new JSONConnector();

		when(controller.apiTimeoutSeconds()).thenReturn(25);
		init(connector);
		OkHttpClient client = client(connector);
		assertEquals(25_000, client.connectTimeoutMillis());
		assertEquals(25_000, client.readTimeoutMillis());
		assertEquals(25_000, client.writeTimeoutMillis());

		when(controller.lazyServer()).thenReturn(true);
		when(controller.lazyServerTimeoutMinutes()).thenReturn(3);
		init(connector);
		client = client(connector);
		assertEquals(180_000, client.connectTimeoutMillis());
		assertEquals(180_000, client.readTimeoutMillis());
		assertEquals(180_000, client.writeTimeoutMillis());

		init(connector);
		assertSame(client, client(connector));
	}

	private static void init(JSONConnector connector) throws Exception {
		Method method = JSONConnector.class.getDeclaredMethod("initHttpClient");
		method.setAccessible(true);
		method.invoke(connector);
	}

	private static OkHttpClient client(JSONConnector connector) throws Exception {
		Field field = JSONConnector.class.getDeclaredField("client");
		field.setAccessible(true);
		return (OkHttpClient) field.get(connector);
	}
}
