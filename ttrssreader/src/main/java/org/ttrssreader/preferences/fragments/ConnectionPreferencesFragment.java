package org.ttrssreader.preferences.fragments;

import android.os.Bundle;
import android.text.InputType;
import android.widget.Toast;

import org.ttrssreader.R;
import org.ttrssreader.net.ApiTimeouts;
import org.ttrssreader.preferences.Constants;

import androidx.annotation.Keep;
import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceFragmentCompat;

@Keep
public class ConnectionPreferencesFragment extends PreferenceFragmentCompat {

	//	private static final String TAG = ConnectionPreferencesFragment.class.getSimpleName();

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
		addPreferencesFromResource(R.xml.prefs_connection);

		// Configure before tryInitializeWifiPrefs() which prefixes the keys with the SSID
		configureTimeoutPreference(Constants.API_TIMEOUT);
		configureTimeoutPreference(Constants.LAZY_SERVER_TIMEOUT);

		WifiPreferencesFragment.tryInitializeWifiPrefs(getContext(), getArguments(), getPreferenceScreen());

		EditTextPreference prefUsername = findPreference("ConnectionUsernamePreference");
		if (prefUsername != null)
			prefUsername.setOnBindEditTextListener(editText -> editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME));

		EditTextPreference prefPassword = findPreference("ConnectionPasswordPreference");
		if (prefPassword != null)
			prefPassword.setOnBindEditTextListener(editText -> editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD));
	}

	private void configureTimeoutPreference(String key) {
		EditTextPreference pref = findPreference(key);
		if (pref == null)
			return;

		pref.setOnBindEditTextListener(editText -> {
			editText.setInputType(InputType.TYPE_CLASS_NUMBER);
			editText.setSelection(editText.getText().length());
		});
		pref.setOnPreferenceChangeListener((preference, newValue) -> {
			if (ApiTimeouts.isValidTimeout(newValue))
				return true;
			if (getContext() != null)
				Toast.makeText(getContext(), R.string.ConnectionTimeoutInvalid, Toast.LENGTH_SHORT).show();
			return false;
		});
	}
}
