package il.tv.israeltv;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;

/**
 * "Open the app when the streamer turns on".
 * - Receiver: runs after the device boots and starts the service.
 * - Service: stays in the background and opens the app every time the screen turns on
 *   (waking from standby), and once right after boot.
 * Opening an app from the background needs the "Display over other apps" permission.
 */
public class AutoStart {
    static final String PREFS = "israeltv";
    static final String KEY_AUTOSTART = "autostart";

    static boolean enabled(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_AUTOSTART, false);
    }

    static boolean canOpenFromBackground(Context c) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(c);
    }

    static void startService(Context c, boolean openNow) {
        Intent i = new Intent(c, WatchService.class).putExtra("openNow", openNow);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) c.startForegroundService(i);
            else c.startService(i);
        } catch (Exception ignored) {
        }
    }

    static void stopService(Context c) {
        c.stopService(new Intent(c, WatchService.class));
    }

    static void openApp(Context c) {
        Intent i = new Intent(c, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        try {
            c.startActivity(i);
        } catch (Exception ignored) {
        }
    }

    /** Device finished booting. */
    public static class BootReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (enabled(context)) startService(context, true);
        }
    }

    /** Background watcher for "screen on". */
    public static class WatchService extends Service {
        private final Handler handler = new Handler(Looper.getMainLooper());
        private BroadcastReceiver screenOn;

        @Override
        public void onCreate() {
            super.onCreate();
            String channelId = "autostart";
            Notification.Builder nb;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationManager nm = getSystemService(NotificationManager.class);
                nm.createNotificationChannel(new NotificationChannel(channelId, "פתיחה אוטומטית",
                        NotificationManager.IMPORTANCE_MIN));
                nb = new Notification.Builder(this, channelId);
            } else {
                nb = new Notification.Builder(this);
            }
            Notification n = nb.setContentTitle("ערוצי ישראל")
                    .setContentText("ייפתח אוטומטית כשהמסך נדלק")
                    .setSmallIcon(android.R.drawable.ic_media_play)
                    .build();
            startForeground(1, n);

            screenOn = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    // Give the home screen a moment to appear, then come to the front.
                    handler.postDelayed(() -> openApp(WatchService.this), 1500);
                }
            };
            registerReceiver(screenOn, new IntentFilter(Intent.ACTION_SCREEN_ON));
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            if (!enabled(this)) {
                stopSelf();
                return START_NOT_STICKY;
            }
            if (intent != null && intent.getBooleanExtra("openNow", false)) {
                handler.postDelayed(() -> openApp(this), 4000);
            }
            return START_STICKY;
        }

        @Override
        public void onDestroy() {
            if (screenOn != null) unregisterReceiver(screenOn);
            super.onDestroy();
        }

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }
    }
}
