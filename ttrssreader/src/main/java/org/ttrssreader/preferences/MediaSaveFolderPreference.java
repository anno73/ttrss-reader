package org.ttrssreader.preferences;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

@Keep
public class MediaSaveFolderPreference extends Preference {
	public MediaSaveFolderPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	public void onBindViewHolder(PreferenceViewHolder holder) {
		super.onBindViewHolder(holder);
		View summary = holder.findViewById(android.R.id.summary);
		if (summary instanceof TextView) {
			TextView text = (TextView) summary;
			text.setMaxLines(Integer.MAX_VALUE);
			text.setEllipsize(null);
		}
	}
}
