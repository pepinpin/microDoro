package net.biospherecorp.microdoro;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;


public class MyPreferenceActivity extends PreferenceActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// get the data from the intent
		int orientation = getIntent().getIntExtra("orientation", ActivityInfo.SCREEN_ORIENTATION_LOCKED);

		// set the orientation as it is in the main activity
		switch (orientation){
			case ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
				break;
			case ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
				break;
			case ActivityInfo.SCREEN_ORIENTATION_PORTRAIT:
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
				break;
			case ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT:
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT);
				break;
		}

		// lock screen orientation
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);

		getFragmentManager().beginTransaction()
				.replace(android.R.id.content, new MyPreferenceFragment())
				.commit();

	}

	public static class MyPreferenceFragment extends PreferenceFragment{

		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.preferences);
		}
	}
}
