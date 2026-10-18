package org.ttrssreader.net;

import android.content.SharedPreferences;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.ttrssreader.controllers.Controller;
import org.ttrssreader.preferences.Constants;

import java.lang.reflect.Field;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ApiPageSizePreferencesTest {
	private MockedStatic<Log> logs;
	private SharedPreferences prefs;
	private Controller controller;

	@Before
	public void setUp() throws Exception {
		logs = mockStatic(Log.class);
		prefs = mock(SharedPreferences.class);
		controller = mock(Controller.class, CALLS_REAL_METHODS);
		Field field = Controller.class.getDeclaredField("prefs");
		field.setAccessible(true);
		field.set(controller, prefs);
		when(prefs.getInt(Constants.API_PAGE_SIZE, 200)).thenReturn(200);
	}

	@After
	public void tearDown() {
		logs.close();
	}

	@Test
	public void defaultAndGlobalPreferenceAreReadWithoutCaching() {
		assertEquals(200, controller.apiPageSize());
		when(prefs.getInt(Constants.API_PAGE_SIZE, 200)).thenReturn(7);
		assertEquals(7, controller.apiPageSize());
	}

	@Test
	public void wifiOverrideFallsBackToGlobalAndCanBeDisabled() throws Exception {
		WifiManager wifi = mock(WifiManager.class);
		WifiInfo info = mock(WifiInfo.class);
		when(wifi.isWifiEnabled()).thenReturn(true);
		when(wifi.getConnectionInfo()).thenReturn(info);
		when(info.getSSID()).thenReturn("\"Home\"");
		Field field = Controller.class.getDeclaredField("wifiManager");
		field.setAccessible(true);
		field.set(controller, wifi);
		when(prefs.getBoolean("Home" + Constants.ENABLE_WIFI_BASED_SUFFIX, false)).thenReturn(true);
		when(prefs.getInt(Constants.API_PAGE_SIZE, 200)).thenReturn(17);
		assertEquals(17, controller.apiPageSize());
		when(prefs.contains("Home" + Constants.API_PAGE_SIZE)).thenReturn(true);
		when(prefs.getInt("Home" + Constants.API_PAGE_SIZE, 200)).thenReturn(3);
		assertEquals(3, controller.apiPageSize());
		when(prefs.getBoolean("Home" + Constants.ENABLE_WIFI_BASED_SUFFIX, false)).thenReturn(false);
		assertEquals(17, controller.apiPageSize());
	}

	@Test
	public void invalidStoredValuesUseDefaultAndLogWarning() {
		when(prefs.getInt(Constants.API_PAGE_SIZE, 200)).thenReturn(0, -1);
		assertEquals(200, controller.apiPageSize());
		assertEquals(200, controller.apiPageSize());
		logs.verify(() -> Log.w(anyString(), contains("Non-positive")), times(2));
		when(prefs.getInt(Constants.API_PAGE_SIZE, 200)).thenThrow(new ClassCastException());
		assertEquals(200, controller.apiPageSize());
		logs.verify(() -> Log.w(anyString(), contains("Invalid integer"), any(ClassCastException.class)));
	}

	@Test
	public void preferenceParticipatesInDefaultsAndReset() {
		assertEquals(200, Constants.getDefaultValueInt(Constants.API_PAGE_SIZE, -1));
		SharedPreferences.Editor editor = mock(SharedPreferences.Editor.class);
		when(prefs.edit()).thenReturn(editor);
		Constants.resetPreferences(prefs);
		verify(editor).putInt(Constants.API_PAGE_SIZE, 200);
		verify(editor).apply();
	}
}
