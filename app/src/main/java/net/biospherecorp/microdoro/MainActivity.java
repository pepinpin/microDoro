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
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.gospelware.liquidbutton.LiquidButton;

import java.lang.ref.WeakReference;

public class MainActivity extends AppCompatActivity {

	// this fields need to be static to be usable by the handler
	//
	private static WeakReference<LiquidButton> LIQUID_BUTTON;
	private static WeakReference<TextView> TEXT_TIME;

	// the notification time
	private static final int NOTIFICATION_TIME = 2500; // in ms

	// variable to hold the time in minutes
	private static int TIME_IN_MN;

	// variable to hold the time in _seconds
	private static float TIME_IN_SEC;

	// is the timer actually running ?
	private static boolean IS_RUNNING = false;


	// the textViews
	private TextView _pressStartTextView,
			_textStartSession, _textInProgress;

	// From Settings
	private int _pomodoroAmount, _pomodoroDuration,
			_shortBreakDuration, _longBreakDuration;

	private boolean _isVibrate, _isSound;

	// the Pomodoro counter
	private int _pomodoroCounter = 0;

	// the current state
	//
	// first run = 100, pomodoro = 0, quick break = -1, long break = -2
	private int _currentState = 100;

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

		// get the textView showing the time
		TEXT_TIME = new WeakReference<>((TextView) findViewById(R.id.text_time));

		// get the liquidButton
		LIQUID_BUTTON = new WeakReference<>((LiquidButton) MainActivity.this.findViewById(R.id.liquid_time));


		// get the default values from the settings
		_getSettingsFromSharedPreferences();

		// set the time with the value from settings
		TEXT_TIME.get().setText(_pomodoroDuration + " mn");

		// get the start session textView
		_textStartSession = (TextView) findViewById(R.id.text_third);

		// get the "in progress..." textView
		_textInProgress = (TextView) findViewById(R.id.text_secondary);

		// find and set the "press start" textView
		_pressStartTextView = (TextView) findViewById(R.id.press_start);
		_pressStartTextView.setText(R.string.text_press_start);

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
				intent.putExtra("orientation", getRequestedOrientation());
				startActivityForResult(intent, 1);
			}
		});


		// the start button
		_startButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {

			// start a new Pomodoro session
				if (!IS_RUNNING){

					// say that is running
					IS_RUNNING = true;

					// lock screen orientation on 1st run
					setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);

					// initialize the TIME_IN_MN,
					// the Buttons, the screen backlighting and the TextViews
					_init();

					// start the timer's thread
					_thread = new Thread(new Chrono());
					_thread.start();

			// cancel the current Pomodoro session
				}else if (!_isCanceledByUser){

					// only true when this button is pressed
					// while a timer is in progress
					_isCanceledByUser = true;

					// if the thread is still running, stop it
					if (!_thread.isInterrupted()){
						_thread.interrupt();
					}

					// turn the screen back light to 80%
					_lightScreenUp();

					// reset the buttons and textViews to original state
					_hideButtonsAndTextViews();

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

				// if the snackBar is visible, dismiss it
				if (_snackBar != null){
					_snackBar.dismiss();
				}

				// show the "press start" and the "in progress..." textViews
				_pressStartTextView.setVisibility(View.VISIBLE);
				_textStartSession.setVisibility(View.VISIBLE);

				// show the buttons
				_startButton.setVisibility(View.VISIBLE);
				_settingButton.setVisibility(View.VISIBLE);

				// it's not running anymore
				IS_RUNNING = false;
			}

			@Override
			public void onProgressUpdate(float progress) {

				// if progress >= 100%
				if (progress >= 1f){

					String textToDisplay;

					// if current state is a break
					if (_currentState < 0){

						// set the "get back to work" message
						textToDisplay = getString(R.string.text_get_to_work);

					// if current state is a pomodoro
					}else{

						// if it's time for a long break
						if (_pomodoroCounter == _pomodoroAmount){

							// set the "Time for a long break" message
							textToDisplay = getString(R.string.text_time_for_long_break);
						}else{

							// set the "Time for a quick break" message
							textToDisplay = getString(R.string.text_time_for_short_break);
						}
					}

					// set the "start..." message
					_textStartSession.setText(textToDisplay);

					// hide the "in progress..." textView
					_textInProgress.setVisibility(View.INVISIBLE);

					// if the timer hasn't been canceled by the user
					if (!_isCanceledByUser){

						// Notify with sound, vibration and notification
						Thread notifyThread = new Thread(new NotifyTimeIsUp(textToDisplay));
						notifyThread.start();

					// if the timer has been canceled by the user
					}else{
						// do nothing
						// just reset the variable to false
						_isCanceledByUser = false;
					}

					// turn the screen backlight up
					_lightScreenUp();
				}
			}
		});
	}

	@Override
	protected void onResume() {
		super.onPostResume();

		// set the flag to avoid the device to go to sleep mode
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
	}

	@Override
	protected void onPause() {
		super.onPause();

		// clear the flag that avoids the device to go to sleep mode
		getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
	}

	// return from the preferences activity
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (requestCode == 1){

			// get the default value from the settings
			_getSettingsFromSharedPreferences();

			// check the current state
			switch (_currentState){
				case -1: // is quick break
					TIME_IN_MN = _shortBreakDuration;
					break;
				case -2: // is long break
					TIME_IN_MN = _longBreakDuration;
					break;
				default: // is pomodoro or 1st run
					TIME_IN_MN = _pomodoroDuration;
					break;
			}

			TEXT_TIME.get().setText(TIME_IN_MN+ " mn");
		}
	}

	private void _init(){

		// if current state is a break or the app has just been launched
		if (_currentState < 0 || _currentState == 100){

			// if the app has just been launched
			if (_currentState == 100){
				//increment the counter
				_pomodoroCounter++;
			}

			// change the state to Pomodoro
			_currentState = 0;

			// set the variable and set the 2nd text textView
			TIME_IN_MN = _pomodoroDuration;
			_textInProgress.setText(R.string.text_pomodoro_in_progress);

		// if current state is a Pomodoro
		}else{

			// and if the counter = settings amount
			if (_pomodoroCounter == _pomodoroAmount){

				// reset the pomodoro counter
				_pomodoroCounter = 0;

				// change the state to long break
				_currentState = -2;

				// set the variable and set the 2nd text textView
				TIME_IN_MN = _longBreakDuration;
				_textInProgress.setText(R.string.text_long_break_in_progress);

			// if the counter != settings amount
			}else{

				// increment the pomodoro counter
				_pomodoroCounter++;

				// change the state to a quick break
				_currentState = -1;

				// set the variable and set the 2nd text textView
				TIME_IN_MN = _shortBreakDuration;
				_textInProgress.setText(R.string.text_short_break_in_progress);
			}
		}

		// store the conversion of minute to _seconds
		TIME_IN_SEC = TIME_IN_MN * 60f;

		// init textViews
		_initTextViews();

		// init the buttons
		_initButtons();

		// dim the screen backlight down
		_lightScreenDown();
	}

	private void _initTextViews(){

		// hide the "press start" and the third text textViews
		_pressStartTextView.setVisibility(View.INVISIBLE);

		// show the main TextView
		TEXT_TIME.get().setVisibility(View.VISIBLE);

		// show the second text
		_textInProgress.setVisibility(View.VISIBLE);

		// set the main textView
		TEXT_TIME.get().setText(TIME_IN_MN + " mn");

		// hide the 3rd text
		_textStartSession.setVisibility(View.INVISIBLE);
	}

	private void _initButtons(){

		// change the image and the background color of the _startButton
		_startButton.setBackgroundTintList(ColorStateList.valueOf(_colorSecondary));
		_startButton.setImageResource(android.R.drawable.ic_delete);

		// hide the settings button
		_settingButton.setVisibility(View.INVISIBLE);

		// setup the liquid button
		//
		// say that the button stays filled up after the animation is complete
		LIQUID_BUTTON.get().setFillAfter(true);
		// start the pouring animation
		LIQUID_BUTTON.get().startPour();
	}

	private void _hideButtonsAndTextViews() {

		// hide the main TextView
		TEXT_TIME.get().setVisibility(View.INVISIBLE);

		// hide the "in progress..." TextView
		_textInProgress.setVisibility(View.INVISIBLE);

		// hide the buttons
		_startButton.setVisibility(View.INVISIBLE);
		_settingButton.setVisibility(View.INVISIBLE);
	}

	private void _getSettingsFromSharedPreferences(){

		// get the shared preferences
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

		// get the values and set the variables
		_pomodoroAmount = Integer.valueOf(sharedPreferences.getString("pomodoro_amount", "4"));
		_pomodoroDuration = Integer.valueOf(sharedPreferences.getString("pomodoro_duration", "25"));
		_shortBreakDuration = Integer.valueOf(sharedPreferences.getString("short_break_duration", "5"));
		_longBreakDuration = Integer.valueOf(sharedPreferences.getString("long_break_duration", "20"));

		_isVibrate = sharedPreferences.getBoolean("notification_vibrate", true);
		_isSound = sharedPreferences.getBoolean("notification_ring", true);
	}

	// turn the screen brightness up
	private void _lightScreenUp(){
		WindowManager.LayoutParams layout = getWindow().getAttributes();
		layout.screenBrightness = -1f;
		getWindow().setAttributes(layout);
	}

	// dim the screen brightness down
	private void _lightScreenDown(){
		WindowManager.LayoutParams layout = getWindow().getAttributes();
		layout.screenBrightness = 0.2f;
		getWindow().setAttributes(layout);
	}


// Runnable used to notify that the time is up
	private class NotifyTimeIsUp implements Runnable{

		private final String _textToUse;

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

		// store the _seconds
		private int _seconds = (int)TIME_IN_SEC;

		// the message needed for the inter thread communication
		private Message _message;

		@Override
		public void run() {

			// while the thread is running
			while(IS_RUNNING && !_thread.isInterrupted()){

				// decrement the _seconds by 1
				_seconds -= 1;

				// get a new message from the pool
				_message = Message.obtain();

				// debug only
//				Log.w("_seconds : ", ""+ _seconds);

				try {
					Thread.sleep(1000); // sleep for 1 _seconds
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				if (_seconds > 0){
					// send the last value
					_message.arg1 = _seconds;
				}else{
					// sends -1 ( means error or end)
					_message.arg1 = -1;
				}

				//send the message
				_handler.sendMessage(_message);
			}

			// stop the thread
			_thread.interrupt();
		}
	}


	// the handler (handles communication between threads)
	private static class mHandler extends Handler {

		private String _valueToDisplay;
		private float _progress;

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
				_progress = msg.arg1 / TIME_IN_SEC;

				// change the progress of the liquidButton
				LIQUID_BUTTON.get().changeProgress(1 - _progress);

				// if the _seconds count is > 60
				if (msg.arg1 > 60){

					// calculate if there is a minute change
					if(msg.arg1 % 60 == 0){

						// if there is, decrement the minute count
						TIME_IN_MN -= 1;
					}

					// store the value
					_valueToDisplay = TIME_IN_MN + " mn";
				}else{
					// if the _seconds count is < 60, store the time in _seconds
					_valueToDisplay = msg.arg1 + " s";
				}

				// if the timer is still running (not interrupted)
				if (IS_RUNNING){
					// set the main TextView with the value
					TEXT_TIME.get().setText(_valueToDisplay);
				}else{
					// otherwise, hide the TextView
					TEXT_TIME.get().setVisibility(View.INVISIBLE);
				}
			}
		}
	}
}
