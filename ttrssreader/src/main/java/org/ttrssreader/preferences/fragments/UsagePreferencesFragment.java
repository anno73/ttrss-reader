package org.ttrssreader.preferences.fragments;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import org.ttrssreader.R;
import org.ttrssreader.preferences.Constants;
import org.ttrssreader.utils.MediaSaveFolder;

import java.io.IOException;

import androidx.annotation.Keep;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

@Keep
public class UsagePreferencesFragment extends PreferenceFragmentCompat {

	private static final String TAG = UsagePreferencesFragment.class.getSimpleName();
	private static final int REQUEST_MEDIA_FOLDER = 14;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		setHasOptionsMenu(true);
		super.onCreate(savedInstanceState);
	}

	@Override
	public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
		addPreferencesFromResource(R.xml.prefs_usage);
		Preference folder = findPreference(Constants.MEDIA_SAVE_FOLDER);
		if (folder != null) {
			folder.setOnPreferenceClickListener(preference -> {
				try {
					startActivityForResult(MediaSaveFolder.pickerIntent(), REQUEST_MEDIA_FOLDER);
				} catch (ActivityNotFoundException | IllegalArgumentException e) {
					Log.e(TAG, "Could not open media folder picker", e);
					Toast.makeText(requireContext(), R.string.MediaSaveFolderPickerUnavailable, Toast.LENGTH_LONG).show();
				}
				return true;
			});
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		updateFolderSummary();
	}

	private void updateFolderSummary() {
		Preference preference = findPreference(Constants.MEDIA_SAVE_FOLDER);
		if (preference == null)
			return;
		Uri folder = MediaSaveFolder.selectedUri();
		if (folder == null) {
			preference.setSummary(R.string.MediaSaveFolderNotSelected);
			return;
		}
		try {
			MediaSaveFolder.requireWritableFolder(requireContext(), folder);
			preference.setSummary(getString(R.string.MediaSaveFolderSelected,
					MediaSaveFolder.displayName(requireContext(), folder)));
		} catch (IOException | SecurityException | IllegalArgumentException | IllegalStateException
				 | SQLiteException | UnsupportedOperationException e) {
			Log.w(TAG, "Saved media folder is inaccessible", e);
			preference.setSummary(getString(R.string.MediaSaveFolderUnavailable, e.getMessage()));
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode != REQUEST_MEDIA_FOLDER || resultCode != Activity.RESULT_OK)
			return;
		try {
			MediaSaveFolder.rememberSelection(requireContext(), data);
		} catch (IOException | SecurityException | IllegalArgumentException | IllegalStateException
				 | SQLiteException | UnsupportedOperationException e) {
			Log.e(TAG, "Could not remember selected media folder", e);
			Toast.makeText(requireContext(), getString(R.string.MediaSaveFolderSelectionFailed, e.getMessage()),
					Toast.LENGTH_LONG).show();
		}
		updateFolderSummary();
	}
}
