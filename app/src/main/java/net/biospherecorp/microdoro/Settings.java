package net.biospherecorp.microdoro;


import android.content.Context;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.NumberPicker;

class Settings {

	static int POMODORO_AMOUNT = 3; // position of the default in the array
	static final String[] POMODORO_AMOUNT_ARRAY = {"2", "3", "4", "5", "6"}; // array of time choices for a Pomodoro session (in mn)

	static int POMODORO_DURATION = 3; // position in the array
	static final String[] POMODORO_DURATION_ARRAY = {"15", "20", "25", "30", "35"}; // array of time choices for a Pomodoro session (in mn)
	private static final String POMODORO_MSG = "Time to get back to work";

	static int SHORT_BREAK_DURATION = 2; // position in the array
	static final String[] SHORT_BREAK_DURATION_ARRAY = {"2", "5", "7", "10"}; // array of time choices for a Pomodoro session (in mn)
	private static final String SHORT_BREAK_MSG = "Time for a quick break";

	static int LONG_BREAK_DURATION = 2; // position in the array
	static final String[] LONG_BREAK_DURATION_ARRAY = {"15", "20", "25", "30"}; // array of time choices for a Pomodoro session (in mn)
	private static final String LONG_BREAK_MSG = "Time for a long break";

	private static int VALUE_POS_IN_ARRAY;


	// Displays the alertDialog used by the Settings section
	static int showNumberPicker(Context ctxt, String[] arrayToUse, int defaultPositionInArray, String title){


		final AlertDialog.Builder adb = new AlertDialog.Builder(ctxt);
		final NumberPicker np = new NumberPicker(ctxt);

		// set the title
		adb.setTitle(title);

		// to make sure the soft keyboard doesn't pop up
		np.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

		// values to use
		np.setDisplayedValues(arrayToUse);

		// set the min index in the Wheel
		np.setMinValue(0);

		// set the max index in the Wheel
		np.setMaxValue(arrayToUse.length -1);

		// some default value
		np.setValue(defaultPositionInArray);

		// does the wheel wrap around
		np.setWrapSelectorWheel(false);

		// set the "Set" button
		adb.setPositiveButton(R.string.settings_set, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialogInterface, int i) {

				VALUE_POS_IN_ARRAY = np.getValue();

				// dismiss this AlertDialog
				dialogInterface.dismiss();
			}
		});

		// set the "Cancel" button
		adb.setNeutralButton(R.string.settings_cancel, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialogInterface, int i) {

				// dismiss this AlertDialog
				dialogInterface.dismiss();
			}
		});

		// this listener is set to allow proper navigation on TVs (with Dpad)
		adb.setOnKeyListener(new DialogInterface.OnKeyListener() {
			@Override
			public boolean onKey(DialogInterface dialogInterface, int i, KeyEvent keyEvent) {

				// if key is released (action up)
				if (keyEvent.getAction() == KeyEvent.ACTION_UP){

					// check fot the keyCode
					switch(keyEvent.getKeyCode()){

						// if it's up
						case KeyEvent.KEYCODE_DPAD_UP:
							// scroll the wheel upwards
							np.setValue(np.getValue() - 1);
							return true;
						// if it's down
						case KeyEvent.KEYCODE_DPAD_DOWN:
							// scroll the wheel downwards
							np.setValue(np.getValue() + 1);
							return true;
						default:
							return false;
					}
				}

				return false;
			}
		});

		// instantiate a new layout for the AlertDialog
		final FrameLayout parent = new FrameLayout(ctxt);

		// set it up with the number picker
		parent.addView(np, new FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.WRAP_CONTENT,
				FrameLayout.LayoutParams.WRAP_CONTENT,
				Gravity.CENTER));

		// add the newly create layout to the AlertDialog
		adb.setView(parent);

		// show the alertDialog
		adb.show();


		return VALUE_POS_IN_ARRAY;
	}
}
