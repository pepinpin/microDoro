package net.biospherecorp.microdoro;

import android.app.NotificationManager;
import android.app.PendingIntent;
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
import android.support.v7.app.AppCompatActivity;
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

	// the notification Time
	private static final int NOTIFICATION_TIME = 2500; // in ms

	// the default time for the timer
	private static final int DEFAULT_TIME_MN = 1;

	// set the variable used to count the minutes
	private static int COUNT_MINUTE = DEFAULT_TIME_MN;

	// variable to hold the time in seconds
	private static float TIME_IN_SEC;

	// is the timer actually running ?
	private static boolean IS_RUNNING = false;




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

		// get the colors
		_colorPrimary = getResources().getColor(R.color.colorPrimary);
		_colorSecondary = getResources().getColor(R.color.colorAccent);

		// get the buttons from the view
		_startButton = (FloatingActionButton) findViewById(R.id.start_button);
		_settingButton = (FloatingActionButton) findViewById(R.id.setting_button);

		// set the buttons background colors
		_startButton.setBackgroundTintList(ColorStateList.valueOf(_colorPrimary));
		_settingButton.setBackgroundTintList(ColorStateList.valueOf(_colorPrimary));

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

					// store the conversion of minute to seconds
					TIME_IN_SEC = COUNT_MINUTE * 60f;

					// initialize the Buttons
					initButtons();

					// initialize the TextViews
					initTextViews();

					// start the timer's thread
					_thread = new Thread(new ChronoMinute());
					_thread.start();

				}else if (!_isCanceledByUser){

					// only true when this button is pressed
					// while a timer is in progress
					_isCanceledByUser = true;

					// if the thread is still alive, stop it
					if (!_thread.isInterrupted()){
						_thread.interrupt();
					}

					// clear the main TextView
					TEXT_TIME.get().setVisibility(View.INVISIBLE);

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

				// resets the buttons state
				resetButtons();

				// reset the UI (textViews & snackBar)
				resetUI();

				// it's not running anymore
				IS_RUNNING = false;
			}

			@Override
			public void onProgressUpdate(float progress) {

				if (progress >= 1f){

					if (!_isCanceledByUser){

						// TEXT
						TEXT_SECONDARY.get().setText(R.string.notification_text);

						Thread notifyThread = new Thread(new NotifyTimeIsUp());
						notifyThread.start();

					}else{

						// reset the variable to false
						_isCanceledByUser = false;

						// display the "Press Start" text
						TEXT_SECONDARY.get().setText(R.string.press_start);
					}
				}
			}
		});
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

	private void initTextViews(){

		// show the main TextView
		TEXT_TIME.get().setVisibility(View.VISIBLE);
		// set the main textView
		TEXT_TIME.get().setText(COUNT_MINUTE + " mn");

		// hide the secondary textView
		TEXT_SECONDARY.get().setVisibility(View.INVISIBLE);
	}

	private void resetButtons(){

		// change the start button background color AND image
		_startButton.setBackgroundTintList(ColorStateList.valueOf(_colorPrimary));
		_startButton.setImageResource(android.R.drawable.ic_media_play);

		// re enabled the settings button
		_settingButton.setEnabled(true);
	}

	private void resetUI(){

		// if the snackBar is visible, dismiss it
		if (_snackBar != null){
			_snackBar.dismiss();
		}

		// show the secondary textView
		TEXT_SECONDARY.get().setVisibility(View.VISIBLE);
	}



// Runnable used to notify that the time is up
	class NotifyTimeIsUp implements Runnable{

		@Override
		public void run() {

			// VIBRATE
			// get the vibrator
			Vibrator mVibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
			mVibrator.vibrate(NOTIFICATION_TIME);


			// NOTIFICATION
			Intent intent = new Intent(MainActivity.this, MainActivity.class);
			PendingIntent pendingIntent = PendingIntent.getActivity(MainActivity.this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

			NotificationCompat.Builder notification =
					new NotificationCompat.Builder(MainActivity.this)
							.setSmallIcon(R.drawable.ic_stat_image_timelapse)
							.setContentTitle(getResources().getString(R.string.app_name))
							.setContentText(getString(R.string.notification_text))
							.setContentIntent(pendingIntent);

			NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
			manager.notify(1, notification.build());


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
					// otherwise, hide the TextView
					TEXT_TIME.get().setVisibility(View.INVISIBLE);
				}
			}
		}
	}
}
