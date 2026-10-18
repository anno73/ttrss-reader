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

package org.ttrssreader.preferences;

import android.app.backup.BackupManager;
import android.os.Bundle;
import android.view.View;

import org.ttrssreader.R;
import org.ttrssreader.controllers.Controller;
import org.ttrssreader.gui.MenuFlavorActivity;
import org.ttrssreader.preferences.fragments.PreferencesFragment;
import org.ttrssreader.utils.AsyncTask;
import org.ttrssreader.utils.Utils;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

public class PreferencesActivity extends MenuFlavorActivity implements PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

	//	private static final String TAG = PreferencesActivity.class.getSimpleName();

	public static final String KEY_SSID = "SSID";
	public static final int ACTIVITY_RELOAD = 45;

	private static AsyncTask<Void, Void, Void> init;
	//	private static final String TITLE_TAG = "settingsActivityTitle";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setTheme(Controller.getInstance().getThemeResource());
		Controller.getInstance().initializeThemeMode();
		setTitle(R.string.PreferencesTitle);
		setContentView(R.layout.preferences);

		setResult(Constants.ACTIVITY_SHOW_PREFERENCES);

		if (savedInstanceState == null) {
			FragmentManager fm = getSupportFragmentManager();
			FragmentTransaction ft = fm.beginTransaction();
			ft.replace(R.id.settings, new PreferencesFragment());
			ft.commit();
		}

		initToolbar();

		OnBackPressedCallback callback = new OnBackPressedCallback(true) {
			@Override
			public void handleOnBackPressed() {
				var frag = getSupportFragmentManager();
				if (frag.getBackStackEntryCount() > 0 && frag.isStateSaved()) {
					frag.popBackStack();
				} else {
					// Avoid calling this callback again by disabling and re-enabling it:
					this.setEnabled(false);
					getOnBackPressedDispatcher().onBackPressed();
					this.setEnabled(true);
				}
			}
		};
		getOnBackPressedDispatcher().addCallback(this, callback);
	}

	private void initToolbar() {
		Toolbar m_Toolbar = findViewById(R.id.toolbar);
		if (m_Toolbar != null) {
			setSupportActionBar(m_Toolbar);
			m_Toolbar.setVisibility(View.VISIBLE);
			m_Toolbar.setNavigationIcon(R.drawable.abc_ic_ab_back_material);
			m_Toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(Controller.getInstance());
	}

	@Override
	protected void onResume() {
		super.onResume();
		PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(Controller.getInstance());
	}

	@Override
	protected void onStop() {
		super.onStop();

		if (init != null) {
			init.cancel(true);
			init = null;
		}

		if (!Utils.checkIsConfigInvalid()) {
			init = new AsyncTask<>() {
				@Override
				protected Void doInBackground(Void... params) {
					Controller.getInstance().initialize(getApplicationContext());
					return null;
				}
			};
			init.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		}
		if (Controller.getInstance().isPreferencesChanged()) {
			new BackupManager(this).dataChanged();
			Controller.getInstance().setPreferencesChanged(false);
		}
	}

	@Override
	public boolean onPreferenceStartFragment(@NonNull PreferenceFragmentCompat caller, @NonNull Preference pref) {
		// Instantiate the new Fragment
		Bundle args = pref.getExtras();
		FragmentManager fm = getSupportFragmentManager();
		String prefFragment = pref.getFragment();
		if (prefFragment != null) {
			Fragment fragment = fm.getFragmentFactory().instantiate(getClassLoader(), prefFragment);
			fragment.setArguments(args);
			fragment.setTargetFragment(caller, 0);

			// Replace the existing Fragment with the new Fragment
			FragmentTransaction ft = fm.beginTransaction();
			ft.replace(R.id.settings, fragment);
			ft.addToBackStack(null);
			ft.commit();
			return true;
		}
		return false;
	}

}
