package ru.gelin.android.weather.notification;

import static ru.gelin.android.weather.notification.Tag.TAG;
import ru.gelin.android.weather.Location;
import ru.gelin.android.weather.SimpleLocation;
import ru.gelin.android.weather.Weather;
import ru.gelin.android.weather.WeatherSource;
import ru.gelin.android.weather.google.GoogleWeatherSource;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

/**
 *  Service to update weather.
 *  Just start it. The new weather values will be wrote to SharedPreferences
 *  (use {@link WeatherStorage} to extract them).
 */
public class UpdateService extends Service implements Runnable {

    /** Manual location preferences key */
    static final String LOCATION = "location";
    /** Verbose extra name for the service start intent. */
    public static String EXTRA_VERBOSE = "verbose";
    /** Success update message */
    static final int SUCCESS = 0;
    /** Failure update message */
    static final int FAILURE = 1;
    
    
    /**
     *  Lock used when maintaining update thread.
     */
    private static Object staticLock = new Object();
    /**
     *  Flag if there is an update thread already running. We only launch a new
     *  thread if one isn't already running.
     */
    static boolean threadRunning = false;
    
    /** Verbose flag */
    boolean verbose = false;
    /** Queried location */
    Location location;
    /** Updated weather */
    Weather weather;
    /** Weather update error */
    Exception updateError;
    
    /**
     *  Starts the service. Convenience method.
     */
    public static void start(Context context) {
        start(context, false);
    }
    
    /**
     *  Starts the service. Convenience method.
     *  If the verbose is true, the update errors will be displayed as toasts. 
     */
    public static void start(Context context, boolean verbose) {
        Intent startIntent = new Intent(context, UpdateService.class);
        startIntent.putExtra(EXTRA_VERBOSE, verbose);
        context.startService(startIntent);
    }
    
    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
        
        synchronized(this) {
            this.verbose = intent.getBooleanExtra(EXTRA_VERBOSE, false);
        }

        synchronized(staticLock) {
            if (threadRunning) {
                return;     // only start processing thread if not already running
            }
            //TODO: insert conditions to skip update
            if (!threadRunning) {
                threadRunning = true;
                new Thread(this).start();
            }
        }
    }
    
    @Override
    public void run() {
        SharedPreferences preferences =
            PreferenceManager.getDefaultSharedPreferences(this);
        synchronized(this) {
            this.location = new SimpleLocation(preferences.getString(LOCATION, ""));
        }
 
        WeatherSource source = new GoogleWeatherSource();
        try {
            Weather weather = source.query(location);
            synchronized(this) {
                this.weather = weather;
            }
            internalHandler.sendEmptyMessage(SUCCESS);
        } catch (Exception e) {
            synchronized(this) {
                this.updateError = e;
            }
            internalHandler.sendEmptyMessage(FAILURE);
        }
    }
    
    /**
     *  Handles weather update result.
     */
    final Handler internalHandler = new Handler() {
        public void handleMessage(Message msg) {
            synchronized(staticLock) {
                threadRunning = false;
            }
            switch (msg.what) {
            case SUCCESS:
                synchronized(UpdateService.this) {
                    Log.i(TAG, "received weather: " + 
                            weather.getLocation().getText() + " " + weather.getTime());
                    WeatherStorage storage = new WeatherStorage(UpdateService.this);
                    storage.save(weather);
                    if (verbose && weather.isEmpty()) {
                        Toast.makeText(UpdateService.this, 
                                getString(R.string.weather_update_empty, location.getText()), 
                                Toast.LENGTH_LONG).show();
                    }
                }
                break;
            case FAILURE:
                synchronized(UpdateService.this) {
                    if (verbose) {
                        Toast.makeText(UpdateService.this, 
                                getString(R.string.weather_update_failed, updateError.getMessage()), 
                                Toast.LENGTH_LONG).show();
                    }
                    Log.w(TAG, "failed to update weather", updateError);
                }
                break;
            }
            stopSelf();
        }
    };
    
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

}
