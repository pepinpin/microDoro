package net.biospherecorp.microdoro;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.gospelware.liquidbutton.LiquidButton;

import java.lang.ref.WeakReference;

public class MainActivity extends AppCompatActivity {

	// this fields need to be static to be usable by the handler
	//
	// Putting a class needing the Context in a HardReference static
	// variable will leak the context, hence the use of a WeakReference
	private static WeakReference<LiquidButton> LIQUID_BUTTON;
	private static WeakReference<TextView> TEXT_TIME;
	private static WeakReference<TextView> TEXT_SECONDARY;

	// the notification time
	private static final int NOTIFICATION_TIME = 2500; // in ms

	// variable to hold the time in minutes
	private static int TIME_IN_MN;

	// variable to hold the time in seconds
	private static float TIME_IN_SEC;

	// is the timer actually running ?
	private static boolean IS_RUNNING = false;


	private TextView _pressStartTextView;

	// From Settings
	private int _pomodoroAmount;
	private int _pomodoroDuration;
	private int _shortBreakDuration;
	private int _longBreakDuration;
	private boolean _isVibrate;
	private boolean _isSound;

	private int _pomodoroCounter = 1;
	private boolean _isBreak = true; // is previous state a break ?


	// has the cancel button been pressed
	private boolean _isCanceledByUser = false;

	// The colors used by the buttons
	private int _colorPrimary, _colorSecondary;

	// the floating Action buttons
	private FloatingActionButton _startButton, _settingButton;

	private Thread _thread;
	private Handler _handler;
	private Snackbar _snackBar;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);

		// get the default value from the settings
		getSettingsFromSharedPreferences();

		// get the textView that's says "press start"
		TEXT_SECONDARY = new WeakReference<>((TextView) findViewById(R.id.text_secondary));

		// get the liquidButton
		LIQUID_BUTTON = new WeakReference<>((LiquidButton) MainActivity.this.findViewById(R.id.liquid_time));

		// get the textView showing the time
		TEXT_TIME = new WeakReference<>((TextView) findViewById(R.id.text_time));

		// set the time with the default value
		TEXT_TIME.get().setText(TIME_IN_MN + " mn");

		// find and set the "press start" textView
		_pressStartTextView = (TextView) findViewById(R.id.press_start);
		_pressStartTextView.setText(R.string.press_start);

		// get the colors
		_colorPrimary = getResources().getColor(R.color.colorPrimary);
		_colorSecondary = getResources().getColor(R.color.colorAccent);

		// get the buttons from the view
		_startButton = (FloatingActionButton) findViewById(R.id.start_button);
		_settingButton = (FloatingActionButton) findViewById(R.id.setting_button);

		// set the buttons background colors
		_startButton.setBackgroundTintList(ColorStateList.valueOf(_colorPrimary));
		_settingButton.setBackgroundTintList(ColorStateList.valueOf(_colorPrimary));

		// instantiate the handler
		_handler = new mHandler();

		// the setting section
		_settingButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {

				// start the settings activity
				Intent intent = new Intent(MainActivity.this, MyPreferenceActivity.class);
				startActivity(intent);
			}
		});


		// the start button
		_startButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {

				// if the timer AND the liquid animations aren't running
				if (!IS_RUNNING){

					// say that's running
					IS_RUNNING = true;

					// initialize the TIME_IN_MN,
					// the Buttons and the TextViews
					init();

					// start the timer's thread
					_thread = new Thread(new Chrono());
					_thread.start();

				}else if (!_isCanceledByUser){

					// only true when this button is pressed
					// while a timer is in progress
					_isCanceledByUser = true;

					// if the thread is still alive, stop it
					if (!_thread.isInterrupted()){
						_thread.interrupt();
					}

					// hide the main TextView
					TEXT_TIME.get().setVisibility(View.INVISIBLE);

					// hide the second TextView
					TEXT_SECONDARY.get().setVisibility(View.INVISIBLE);

					// trigger the "finish pouring" animation
					LIQUID_BUTTON.get().finishPour();

					// display a snackBar asking the user to wait for the end of the animation
					_snackBar = Snackbar.make(view, R.string.wait_message, Snackbar.LENGTH_INDEFINITE);
					_snackBar.show();
				}
			}
		});



		// set a listener on the liquid button
		LIQUID_BUTTON.get().setPourFinishListener(new LiquidButton.PourFinishListener() {

			@Override
			public void onPourFinish() { // when the pouring animation is finished

				// change the start button background color AND image
				_startButton.setBackgroundTintList(ColorStateList.valueOf(_colorPrimary));
				_startButton.setImageResource(android.R.drawable.ic_media_play);

				// re enabled the settings button
				_settingButton.setEnabled(true);

				// show the "press start" textView
				_pressStartTextView.setVisibility(View.VISIBLE);

				TEXT_SECONDARY.get().setVisibility(View.VISIBLE);

				// if the snackBar is visible, dismiss it
				if (_snackBar != null){
					_snackBar.dismiss();
				}

				// it's not running anymore
				IS_RUNNING = false;
			}

			@Override
			public void onProgressUpdate(float progress) {

				if (progress >= 1f){

					String textToDisplay;

					if (_isBreak){

						textToDisplay = getString(R.string.text_get_to_work);
					}else{
						if (_pomodoroCounter == _pomodoroAmount){

							textToDisplay = getString(R.string.text_time_for_long_break);
						}else{

							textToDisplay = getString(R.string.text_time_for_short_break);
						}
					}

					TEXT_SECONDARY.get().setText(textToDisplay);

					if (!_isCanceledByUser){

						Thread notifyThread = new Thread(new NotifyTimeIsUp(textToDisplay));
						notifyThread.start();
					}else{

						// reset the variable to false
						_isCanceledByUser = false;
					}
				}
			}
		});
	}

	@Override
	protected void onResume() {
		super.onPostResume();

		// get the default value from the settings
		getSettingsFromSharedPreferences();

		TEXT_TIME.get().setText(_pomodoroDuration + " mn");
	}

	private void init(){

		if (_isBreak){
			_isBreak = false;

			TIME_IN_MN = _pomodoroDuration;
			TEXT_SECONDARY.get().setText(R.string.text_podoro_in_progress);
		}else{
			_isBreak = true;

			if (_pomodoroCounter == _pomodoroAmount){

				_pomodoroCounter = 0;

				TIME_IN_MN = _longBreakDuration;
				TEXT_SECONDARY.get().setText(R.string.text_long_break_in_progress);
			}else{

				_pomodoroCounter++;

				TIME_IN_MN = _shortBreakDuration;
				TEXT_SECONDARY.get().setText(R.string.text_short_break_in_progress);
			}
		}

		// hide the "press start" textView
		_pressStartTextView.setVisibility(View.INVISIBLE);

		// show the main TextView
		TEXT_TIME.get().setVisibility(View.VISIBLE);
		// set the main textView
		TEXT_TIME.get().setText(TIME_IN_MN + " mn");

		// store the conversion of minute to seconds
		TIME_IN_SEC = TIME_IN_MN * 60f;


		initButtons();
	}

	private void initButtons(){

		// change the image and the background color of the _startButton
		_startButton.setBackgroundTintList(ColorStateList.valueOf(_colorSecondary));
		_startButton.setImageResource(android.R.drawable.ic_delete);

		// disable the setting button
		_settingButton.setEnabled(false);

		// setup the liquid button
		//
		// say that the button stays filled up after the animation is complete
		LIQUID_BUTTON.get().setFillAfter(true);
		// start the pouring animation
		LIQUID_BUTTON.get().startPour();
	}

	private void getSettingsFromSharedPreferences(){

		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

		_pomodoroAmount = Integer.valueOf(sharedPreferences.getString("pomodoro_amount", "4"));
		_pomodoroDuration = Integer.valueOf(sharedPreferences.getString("pomodoro_duration", "25"));
		_shortBreakDuration = Integer.valueOf(sharedPreferences.getString("short_break_duration", "5"));
		_longBreakDuration = Integer.valueOf(sharedPreferences.getString("long_break_duration", "20"));

		_isVibrate = sharedPreferences.getBoolean("notification_vibrate", true);
		_isSound = sharedPreferences.getBoolean("notification_ring", true);

		// store the conversion of minute to seconds
		TIME_IN_MN = _pomodoroDuration;




		Log.i("_pomodoroAmount : ", "" + _pomodoroAmount);
		Log.i("_pomodoroDuration : ", "" + _pomodoroDuration);
		Log.i("_shortBreakDuration : ", "" + _shortBreakDuration);
		Log.i("_longBreakDuration : ", "" + _longBreakDuration);
		Log.i("_isVibrate : ", "" + _isVibrate);
		Log.i("_isSound : ", "" + _isSound);
		Log.i("TIME_IN_MN : ", "" + TIME_IN_MN);
	}



// Runnable used to notify that the time is up
	private class NotifyTimeIsUp implements Runnable{

		private String _textToUse;

		NotifyTimeIsUp(String text){
			_textToUse = text;
		}

		@Override
		public void run() {

			// NOTIFICATION
			Intent intent = new Intent(MainActivity.this, MainActivity.class);
			PendingIntent pendingIntent = PendingIntent.getActivity(MainActivity.this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

			NotificationCompat.Builder notification =
					new NotificationCompat.Builder(MainActivity.this)
							.setSmallIcon(R.drawable.ic_stat_image_timelapse)
							.setContentTitle(getResources().getString(R.string.app_name))
							.setContentText(_textToUse)
							.setContentIntent(pendingIntent);

			NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
			manager.notify(1, notification.build());

			if (_isVibrate){

				// VIBRATE
				// get the vibrator
				Vibrator mVibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
				mVibrator.vibrate(NOTIFICATION_TIME);
			}

			if (_isSound){

				// PLAY SOUND
				MediaPlayer player = MediaPlayer.create(MainActivity.this,
						R.raw.kitchen_timer_ringtone);
				player.setVolume(0.8f, 0.8f);
				player.start();

				try {
					Thread.sleep(NOTIFICATION_TIME);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				player.stop();
				player.reset();
				player.release();
			}

		}
	}

	// the runnable used by the timer
	private class Chrono implements Runnable {

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

				// hide the TextView
				TEXT_TIME.get().setVisibility(View.INVISIBLE);

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
						TIME_IN_MN -= 1;
					}

					// store the value
					valueToDisplay = TIME_IN_MN + " mn";
				}else{
					// if the seconds count is < 60, store the time in seconds
					valueToDisplay = msg.arg1 + " s";
				}

				// if the timer is still running (not interrupted)
				if (IS_RUNNING){
					// set the main TextView with the value
					TEXT_TIME.get().setText(valueToDisplay);
				}else{
					// otherwise, hide the TextView
					TEXT_TIME.get().setVisibility(View.INVISIBLE);
				}
			}
		}
	}
}
