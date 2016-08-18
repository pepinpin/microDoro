package net.biospherecorp.microdoro;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.NumberPicker;
import android.widget.TextView;

import com.gospelware.liquidbutton.LiquidButton;

import java.lang.ref.WeakReference;

public class MainActivity extends AppCompatActivity {

	// this fields need to be static to be usable by the handler
	// but putting a class needing the Context in a HardReference static
	// variable will leak the context, hence the use of a WeakReference
	private static WeakReference<LiquidButton> LIQUID_BUTTON;
	private static WeakReference<TextView> TEXT_TIME;
	private static WeakReference<TextView> TEXT_SECONDARY;

	// is the timer actually running ?
	private static boolean IS_RUNNING = false;

	// has the cancel button been pressed
	private static boolean IS_TIMER_CANCELED = false;

	// is the liquid pouring animation running ?
	private static boolean IS_LIQUID_POURING = false;

	// the default time for the timer
	private static final int DEFAULT_TIME_MN = 1;

	// the position of the default time value in the array
	private static int VALUE_IN_ARRAY_TIME = 3;

	// set the variable used to count the minutes
	private static int COUNT_MINUTE = DEFAULT_TIME_MN;

	// variable to hold the time in seconds
	private static float TIME_IN_SEC;

	// The colors used by the buttons
	private static int COLOR_PRIMARY, COLOR_SECONDARY;

	// the notification Time
	private static final int NOTIFICATION_TIME = 2500; // in ms

	// the floating Action buttons
	private FloatingActionButton startButton;
	private FloatingActionButton settingButton;

	private Thread _thread;
	private Handler _handler;
	private Snackbar _snackBar;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);

		// get the colors
		COLOR_PRIMARY = getResources().getColor(R.color.colorPrimary);
		COLOR_SECONDARY = getResources().getColor(R.color.colorAccent);

		// get the buttons from the view
		startButton = (FloatingActionButton) findViewById(R.id.start_button);
		settingButton = (FloatingActionButton) findViewById(R.id.setting_button);

		// set the buttons background colors
		startButton.setBackgroundTintList(ColorStateList.valueOf(COLOR_PRIMARY));
		settingButton.setBackgroundTintList(ColorStateList.valueOf(COLOR_PRIMARY));

		// get the textView that's says "press start"
		TEXT_SECONDARY = new WeakReference<>((TextView) findViewById(R.id.text_secondary));

		// get the textView showing the time
		TEXT_TIME = new WeakReference<>((TextView) findViewById(R.id.text_time));

		// set the time with the default value
		TEXT_TIME.get().setText(DEFAULT_TIME_MN + " mn");

		// get the liquidButton
		LIQUID_BUTTON = new WeakReference<>((LiquidButton) MainActivity.this.findViewById(R.id.liquid_time));

		// instantiate the handler
		_handler = new mHandler();

		// the setting section
		settingButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				// show the alertDialog &
				// the number picker
				showNumberPicker();
			}
		});

		// the start button
		startButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {

				// if the timer AND the liquid animations aren't running
				if (!IS_RUNNING && !IS_LIQUID_POURING){

					// say that's running
					IS_RUNNING = true;

					// store the conversion of minute to seconds
					TIME_IN_SEC = COUNT_MINUTE * 60f;

					// initialize the Buttons
					initButtons();

					// initialize the TextViews
					initTextViews();

					// start the timer's thread
					_thread = new Thread(new ChronoMinute());
					_thread.start();

				}else{

					// only true when this button is pressed
					IS_TIMER_CANCELED = true;

					// say that's not running anymore
					IS_RUNNING = false;

					// trigger the "finish pouring" animation
					LIQUID_BUTTON.get().finishPour();

					// if the thread is still alive, stop it
					if (!_thread.isInterrupted()){
						_thread.interrupt();
					}

					// display a snackBar asking the user to wait for the end of the animation
					_snackBar = Snackbar.make(view, R.string.wait_message, Snackbar.LENGTH_INDEFINITE);
					_snackBar.show();

					// clear the main TextView
					TEXT_TIME.get().setText("");
				}
			}
		});

		// set a listener on the liquid button
		LIQUID_BUTTON.get().setPourFinishListener(new LiquidButton.PourFinishListener() {

			// if the pouring animation is finished
			@Override
			public void onPourFinish() { // if the pouring animation is finished

				// say that it's finished
				IS_LIQUID_POURING = false;

				// vibrate, notify and ring to say
				// that the time is over
				notifyTimerFinished();

				// resets the buttons state
				resetButtons();

				// reset the textViews
				resetTextViews();

				// if the snackBar is visible, dismiss it
				if (_snackBar != null){
					_snackBar.dismiss();
				}

				// hide the secondary textView
				TEXT_SECONDARY.get().setVisibility(View.VISIBLE);
			}

			@Override
			public void onProgressUpdate(float progress) {
				// nothing here
			}
		});
	}


	private void initButtons(){

		// change the image and the background color of the startButton
		startButton.setBackgroundTintList(ColorStateList.valueOf(COLOR_SECONDARY));
		startButton.setImageResource(android.R.drawable.ic_delete);

		// disable the setting button
		settingButton.setEnabled(false);

		// setup the liquid button
		//
		// say that the button stays filled up after the animation is complete
		LIQUID_BUTTON.get().setFillAfter(true);
		// start the pouring animation
		LIQUID_BUTTON.get().startPour();
		// say that the animation is ongoing
		IS_LIQUID_POURING = true;
	}

	private void initTextViews(){

		// hide the "Press Start" textView
		// get the textView that's says "press start"
		TextView pressStart = (TextView) findViewById(R.id.text_secondary);
		pressStart.setVisibility(View.GONE);

		// set the main textView
		TEXT_TIME.get().setText(COUNT_MINUTE + " mn");
	}

	private void resetButtons(){

		// change the start button background color AND image
		startButton.setBackgroundTintList(ColorStateList.valueOf(COLOR_PRIMARY));
		startButton.setImageResource(android.R.drawable.ic_media_play);

		// re enabled the settings button
		settingButton.setEnabled(true);
	}

	private void resetTextViews(){

		// hide the secondary textView
		// get the textView that's says "press start"
		TEXT_SECONDARY.get().setVisibility(View.VISIBLE);
	}


	// Method taking care of all the post process
	// when time is over, it rings, vibrates...
	//
	// only called by the onPourFinish callback
	private void notifyTimerFinished(){

		// if  triggered by the thread ending normally
		// (not by the cancel button)
		if (!IS_TIMER_CANCELED){

			// TEXT
			TEXT_SECONDARY.get().setText(R.string.notification_text);

			// VIBRATE
			// get the vibrator
			Vibrator mVibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
			mVibrator.vibrate(NOTIFICATION_TIME);

			// NOTIFICATION
			Intent intent = new Intent(this, MainActivity.class);
			PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

			NotificationCompat.Builder notification =
					new NotificationCompat.Builder(this)
							.setSmallIcon(R.drawable.ic_stat_image_timelapse)
							.setContentTitle(getResources().getString(R.string.app_name))
							.setContentText(getString(R.string.notification_text))
							.setContentIntent(pendingIntent);

			NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
			manager.notify(1, notification.build());


			// PLAY SOUND
			MediaPlayer player = MediaPlayer.create(this,
					R.raw.kitchen_timer_ringtone);
			player.setVolume(0.8f, 0.8f);
			player.start();

			try {
				Thread.sleep(NOTIFICATION_TIME);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			player.stop();
			player.release();

			// if triggered by the cancel button
		}else{
			// reset the variable to false
			IS_TIMER_CANCELED = false;

			// display the "Press Start" text
			TEXT_SECONDARY.get().setText(R.string.press_start);
		}
	}


	// Displays the alertDialog used by the Settings section
	private void showNumberPicker(){

		// the array holding the usable values
		final String[] TIME_ARRAY = new String[102];

		// add some info about egg cooking
		TIME_ARRAY[0] = getString(R.string.boiled_egg_cooking_time);
		TIME_ARRAY[1] = getString(R.string.soft_boiled_egg_cooking_time);
		TIME_ARRAY[2] = getString(R.string.hard_boiled_egg_cooking_time);

		// fill up the array used by the settings number picker (from 1 to 99)
		for (int i = 3; i <= 101; i++){
			TIME_ARRAY[i] = String.valueOf(i - 2); // to deduce the value from the index
		}

		final AlertDialog.Builder adb = new AlertDialog.Builder(this);
		final NumberPicker np = new NumberPicker(this);

		// set the title
		adb.setTitle(R.string.settings_title);

		// to make sure the soft keyboard doesn't pop up
		np.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

		// values to use
		np.setDisplayedValues(TIME_ARRAY);

		// set the min index in the Wheel
		np.setMinValue(0);

		// set the max index in the Wheel
		np.setMaxValue(TIME_ARRAY.length -1);

		// some default value
		np.setValue(VALUE_IN_ARRAY_TIME);

		// does the wheel wrap around
		np.setWrapSelectorWheel(true);

		// set the "Set" button
		adb.setPositiveButton(R.string.settings_set, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialogInterface, int i) {

				// store the value chosen (the position in the TIME_ARRAY)
				VALUE_IN_ARRAY_TIME = np.getValue();

				// check if it's a egg cooking time
				switch (VALUE_IN_ARRAY_TIME){
					case 0:
						COUNT_MINUTE = 3;
						break;
					case 1:
						COUNT_MINUTE = 5;
						break;
					case 2:
						COUNT_MINUTE = 10;
						break;

					// if not
					default:
						// store the chosen value (the proper value in mn)
						COUNT_MINUTE = Integer.valueOf(TIME_ARRAY[VALUE_IN_ARRAY_TIME]);
				}

				// display the new value in the main TextView
				TEXT_TIME.get().setText(COUNT_MINUTE + " mn");

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
		final FrameLayout parent = new FrameLayout(this);

		// set it up with the number picker
		parent.addView(np, new FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.WRAP_CONTENT,
				FrameLayout.LayoutParams.WRAP_CONTENT,
				Gravity.CENTER));

		// add the newly create layout to the AlertDialog
		adb.setView(parent);

		// show the alertDialog
		adb.show();
	}


	// the runnable used by the thread
	private class ChronoMinute implements Runnable {

		// store the seconds
		int seconds = (int)TIME_IN_SEC;

		// the message needed for the inter thread communication
		Message message;

		@Override
		public void run() {

			// while the thread is running
			while(IS_RUNNING && !_thread.isInterrupted()){

				// decrement the seconds by 1
				seconds -= 1;

				// get a new message from the pool
				message = Message.obtain();

				// debug only
//				Log.w("seconds : ", ""+ seconds);

				try {
					Thread.sleep(1000); // sleep for 1 seconds
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				if (seconds > 0){
					// send the last value
					message.arg1 = seconds;
				}else{
					// sends -1 ( means error or end)
					message.arg1 = -1;
				}

				//send the message
				_handler.sendMessage(message);
			}

			// stop the thread
			_thread.interrupt();
		}
	}



	// the handler (handles communication between threads)
	private static class mHandler extends Handler {

		String valueToDisplay;
		float progress;

		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);

			// if the message = -1, stop the timer
			if (msg.arg1 == -1){

				// say that's not running anymore
				IS_RUNNING = false;

				// resets the main textView
				TEXT_TIME.get().setText("");

				// start the "finishPour" animation
				LIQUID_BUTTON.get().finishPour();

			}else{

				// calculate the progress and store it as a float (1f = 100%)
				progress = msg.arg1 / TIME_IN_SEC;

				// change the progress of the liquidButton
				LIQUID_BUTTON.get().changeProgress(1 - progress);

				// if the seconds count is > 60
				if (msg.arg1 > 60){

					// calculate if there is a minute change
					if(msg.arg1 % 60 == 0){

						// if there is, decrement the minute count
						COUNT_MINUTE -= 1;
					}

					// store the value
					valueToDisplay = COUNT_MINUTE + " mn";
				}else{
					// if the seconds count is < 60, store the time in seconds
					valueToDisplay = msg.arg1 + " s";
				}

				// if the timer is still running (not interrupted)
				if (IS_RUNNING){
					// set the main TextView with the value
					TEXT_TIME.get().setText(valueToDisplay);
				}else{
					// otherwise, reset the TextView
					TEXT_TIME.get().setText("");
				}
			}
		}
	}
}
