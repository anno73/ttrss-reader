package org.ttrssreader.preferences;

import android.content.SharedPreferences;
import android.util.Log;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.ttrssreader.controllers.Controller;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class AutoplayVideosPreferenceTest {
	private MockedStatic<Log> logs;
	private SharedPreferences prefs;
	private Controller controller;
	private final Map<String, Boolean> booleans = new HashMap<>();

	@Before
	public void setUp() throws Exception {
		logs = mockStatic(Log.class);
		prefs = mock(SharedPreferences.class);
		controller = mock(Controller.class, CALLS_REAL_METHODS);
		Field field = Controller.class.getDeclaredField("prefs");
		field.setAccessible(true);
		field.set(controller, prefs);
		when(prefs.getBoolean(anyString(), anyBoolean())).thenAnswer(invocation ->
				booleans.getOrDefault(invocation.getArgument(0), invocation.getArgument(1)));
	}

	@After
	public void tearDown() {
		logs.close();
	}

	@Test
	public void defaultIsAutoplay() {
		assertEquals("AutoplayVideosPreference", Constants.AUTOPLAY_VIDEOS);
		assertTrue(Constants.AUTOPLAY_VIDEOS_DEFAULT);
	}

	@Test
	public void upgradedInstallWithoutStoredValueAutoplays() {
		assertTrue(controller.autoplayVideos());
		verify(prefs).getBoolean(Constants.AUTOPLAY_VIDEOS, true);
	}

	@Test
	public void storedValueIsHonoredWithoutCaching() {
		booleans.put(Constants.AUTOPLAY_VIDEOS, false);
		assertFalse(controller.autoplayVideos());
		booleans.put(Constants.AUTOPLAY_VIDEOS, true);
		assertTrue(controller.autoplayVideos());
	}

	@Test
	public void resetRestoresAutoplayDefault() {
		SharedPreferences.Editor editor = mock(SharedPreferences.Editor.class);
		when(prefs.edit()).thenReturn(editor);
		Constants.resetPreferences(prefs);
		verify(editor).putBoolean(Constants.AUTOPLAY_VIDEOS, true);
		verify(editor).apply();
	}
}
