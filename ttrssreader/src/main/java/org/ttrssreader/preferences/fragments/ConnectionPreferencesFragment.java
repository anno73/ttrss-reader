package org.ttrssreader.preferences.fragments;

import android.os.Bundle;
import android.text.InputType;
import android.widget.Toast;

import org.ttrssreader.R;
import org.ttrssreader.net.ApiTimeouts;
import org.ttrssreader.net.ApiPageSize;
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
		configureIntegerPreference(Constants.API_TIMEOUT, R.string.ConnectionTimeoutInvalid);
		configureIntegerPreference(Constants.LAZY_SERVER_TIMEOUT, R.string.ConnectionTimeoutInvalid);
		configureIntegerPreference(Constants.API_PAGE_SIZE, R.string.ConnectionPageSizeInvalid);

		WifiPreferencesFragment.tryInitializeWifiPrefs(getContext(), getArguments(), getPreferenceScreen());

		EditTextPreference prefUsername = findPreference("ConnectionUsernamePreference");
		if (prefUsername != null)
			prefUsername.setOnBindEditTextListener(editText -> editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME));

		EditTextPreference prefPassword = findPreference("ConnectionPasswordPreference");
		if (prefPassword != null)
			prefPassword.setOnBindEditTextListener(editText -> editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD));
	}

	private void configureIntegerPreference(String key, int errorMessage) {
		EditTextPreference pref = findPreference(key);
		if (pref == null)
			return;

		pref.setOnBindEditTextListener(editText -> {
			editText.setInputType(InputType.TYPE_CLASS_NUMBER);
			editText.setSelection(editText.getText().length());
		});
		pref.setOnPreferenceChangeListener((preference, newValue) -> {
			boolean valid = Constants.API_PAGE_SIZE.equals(key)
					? ApiPageSize.isValid(newValue) : ApiTimeouts.isValidTimeout(newValue);
			if (valid)
				return true;
			if (getContext() != null)
				Toast.makeText(getContext(), errorMessage, Toast.LENGTH_SHORT).show();
			return false;
		});
	}
}
