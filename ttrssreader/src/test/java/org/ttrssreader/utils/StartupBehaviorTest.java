package org.ttrssreader.utils;

import android.content.SharedPreferences;
import android.util.Log;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.ttrssreader.controllers.Controller;
import org.ttrssreader.preferences.Constants;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class StartupBehaviorTest {
	private MockedStatic<Controller> controllers;
	private MockedStatic<Log> logs;
	private SharedPreferences prefs;
	private SharedPreferences.Editor editor;
	private Controller controller;
	private final Map<String, Boolean> booleans = new HashMap<>();

	@Before
	public void setUp() throws Exception {
		logs = mockStatic(Log.class);
		prefs = mock(SharedPreferences.class);
		editor = mock(SharedPreferences.Editor.class);
		controller = mock(Controller.class, CALLS_REAL_METHODS);
		Field field = Controller.class.getDeclaredField("prefs");
		field.setAccessible(true);
		field.set(controller, prefs);
		controllers = mockStatic(Controller.class);
		controllers.when(Controller::getInstance).thenReturn(controller);
		when(prefs.getBoolean(anyString(), anyBoolean())).thenAnswer(invocation ->
				booleans.getOrDefault(invocation.getArgument(0), invocation.getArgument(1)));
		when(prefs.getString(anyString(), anyString())).thenAnswer(invocation -> invocation.getArgument(1));
		when(prefs.edit()).thenReturn(editor);
		when(editor.putBoolean(anyString(), anyBoolean())).thenAnswer(invocation -> {
			booleans.put(invocation.getArgument(0), invocation.getArgument(1));
			return editor;
		});
	}

	@After
	public void tearDown() {
		controllers.close();
		logs.close();
	}

	@Test
	public void freshInstallShowsWelcomeOnceWithoutTrackingTheInstalledVersion() {
		assertTrue(Utils.checkIsFirstRun());
		assertFalse(Utils.checkIsFirstRun());
		verify(editor).putBoolean(Constants.IS_FIRST_RUN, false);
		verify(editor).apply();
		verify(editor, never()).putString(anyString(), anyString());
		verify(editor, never()).putLong(anyString(), anyLong());
	}

	@Test
	public void existingInstallDoesNotShowWelcomeOrReadLegacyVersion() {
		booleans.put(Constants.IS_FIRST_RUN, false);
		assertFalse(Utils.checkIsFirstRun());
		verify(prefs, never()).getString(anyString(), anyString());
		verify(prefs, never()).edit();
	}

	@Test
	public void legacyInstallWithoutFirstRunFlagDoesNotShowWelcome() {
		when(prefs.getString("LastVersionRun", "1")).thenReturn("1.98.2");
		assertFalse(Utils.checkIsFirstRun());
		assertFalse(Utils.checkIsFirstRun());
		verify(editor, never()).putString(anyString(), anyString());
	}

	@Test
	public void legacyVersionSentinelStillAllowsFreshInstallWelcome() {
		when(prefs.getString("LastVersionRun", "1")).thenReturn("1");
		assertTrue(Utils.checkIsFirstRun());
		assertFalse(Utils.checkIsFirstRun());
	}

	@Test
	public void differentlyNamedBuildsNeverTriggerOnboardingForAnExistingInstall() {
		booleans.put(Constants.IS_FIRST_RUN, false);
		when(prefs.getString("LastVersionRun", "1")).thenReturn("master_old-build", "feature_new-build");
		assertFalse(Utils.checkIsFirstRun());
		assertFalse(Utils.checkIsFirstRun());
		verify(prefs, never()).getString(anyString(), anyString());
		verify(prefs, never()).edit();
	}

	@Test
	public void connectionValidationStillDetectsMissingServerAfterUpgrade() throws Exception {
		booleans.put(Constants.IS_FIRST_RUN, false);
		doReturn(null).when(controller).uri();
		assertFalse(Utils.checkIsFirstRun());
		assertTrue(Utils.checkIsConfigInvalid());
		doReturn(new URI(Constants.URL_DEFAULT + Controller.JSON_END_URL)).when(controller).uri();
		assertTrue(Utils.checkIsConfigInvalid());
		doReturn(new URI("https://example.org/tt-rss/api/index.php")).when(controller).uri();
		assertFalse(Utils.checkIsConfigInvalid());
	}

	@Test
	public void invalidSinceIdIsResetWithoutTheRemovedVersionPreference() {
		when(prefs.getInt(Constants.SINCE_ID, Constants.SINCE_ID_DEFAULT))
				.thenThrow(new ClassCastException()).thenReturn(Constants.SINCE_ID_DEFAULT);
		assertEquals(Constants.SINCE_ID_DEFAULT, controller.getSinceId());
		verify(editor).putInt(Constants.SINCE_ID, Constants.SINCE_ID_DEFAULT);
		verify(editor).apply();
		verify(editor, never()).putString(anyString(), anyString());
	}
}
