package com.study.pomodoro;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.util.Locale;

/**
 * 前台服务：常驻通知 + 系统 Chronometer 每秒自动走字 + 准点完成闹钟。
 * 结构与 Timety 的 FocusTimerService 一致：setOngoing + Chronometer + setExactAndAllowWhileIdle。
 */
public class TimerForegroundService extends Service {

    public static final String CHANNEL_ID = "study_timer";
    public static final int NOTIFICATION_ID = 1001;

    private static final String ACTION_START = "com.study.pomodoro.START";
    private static final String ACTION_STOP = "com.study.pomodoro.STOP";
    private static final String ACTION_STOP_BTN = "com.study.pomodoro.STOP_BTN";
    private static final String ACTION_PAUSE_BTN = "com.study.pomodoro.PAUSE_BTN";
    private static final String ACTION_RESUME_BTN = "com.study.pomodoro.RESUME_BTN";
    private static final String EXTRA_CONFIG = "config";

    private static final int ALARM_FOCUS = 9001;
    private static final int ALARM_BREAK = 9002;
    private static final int ALARM_COUNTDOWN = 9003;

    private Handler tickHandler;
    private Runnable tickRunnable;
    private Config lastCfg;
    private long cfgStartElapsed;
    private long cfgTotalMs;

    public static void ensureChannel(Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "专注计时", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("显示计时状态，不响铃");
            nm.createNotificationChannel(ch);
        }
    }

    public static void start(Context context, JSONObject config) {
        try {
            ensureChannel(context);
            Intent i = new Intent(context, TimerForegroundService.class);
            i.setAction(ACTION_START);
            i.putExtra(EXTRA_CONFIG, config == null ? "{}" : config.toString());
            ContextCompat.startForegroundService(context, i);
        } catch (Throwable t) {
            // 后台启动限制等异常：忽略，前台服务通常已在运行
        }
    }

    public static void stop(Context context) {
        try {
            Intent i = new Intent(context, TimerForegroundService.class);
            i.setAction(ACTION_STOP);
            context.startService(i);
        } catch (Throwable t) {
            // 服务未在运行时忽略
        }
    }

    public static void scheduleDone(Context context, long at, String phase, String soundMode, String lang, String title, String body) {
        try {
            int code = ALARM_FOCUS;
            if ("shortBreak".equals(phase) || "longBreak".equals(phase)) code = ALARM_BREAK;
            else if ("countdown".equals(phase)) code = ALARM_COUNTDOWN;

            Intent intent = new Intent(context, TimerSoundReceiver.class);
            intent.setAction(TimerSoundReceiver.ACTION_DONE);
            intent.putExtra(TimerSoundReceiver.EXTRA_PHASE, phase);
            intent.putExtra(TimerSoundReceiver.EXTRA_SOUND_MODE, soundMode);
            intent.putExtra(TimerSoundReceiver.EXTRA_LANG, lang);
            intent.putExtra(TimerSoundReceiver.EXTRA_TITLE, title);
            intent.putExtra(TimerSoundReceiver.EXTRA_BODY, body);
            PendingIntent pi = PendingIntent.getBroadcast(context, code, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            // 与 Timety 相同的守卫：Android 12+ 精确闹钟权限可能被收回
            boolean canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms();
            if (!canExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                try {
                    canExact = context.checkSelfPermission(android.Manifest.permission.USE_EXACT_ALARM)
                            == PackageManager.PERMISSION_GRANTED;
                } catch (Throwable ignore) {
                    canExact = false;
                }
            }
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch (Throwable t) {
            // 闹钟权限异常：忽略，前台服务通知仍会刷新
        }
    }

    public static void cancelDone(Context context) {
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            for (int code : new int[]{ALARM_FOCUS, ALARM_BREAK, ALARM_COUNTDOWN}) {
                Intent intent = new Intent(context, TimerSoundReceiver.class);
                intent.setAction(TimerSoundReceiver.ACTION_DONE);
                PendingIntent pi = PendingIntent.getBroadcast(context, code, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                am.cancel(pi);
            }
        } catch (Throwable t) {
            // 忽略
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            if (intent == null) {
                // START_STICKY 重启但没有意图：结束空跑，避免僵尸常驻
                stopTicker();
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
                return START_NOT_STICKY;
            }
            String action = intent.getAction();
            if (ACTION_START.equals(action)) {
                Config cfg = parse(intent.getStringExtra(EXTRA_CONFIG));
                lastCfg = cfg;
                if (cfg.isRunning && !cfg.isPaused) {
                    cfgStartElapsed = SystemClock.elapsedRealtime();
                    cfgTotalMs = cfg.remainingMs;
                    startTicker();
                } else {
                    stopTicker();
                }
                if (Build.VERSION.SDK_INT >= 34) {
                    startForeground(NOTIFICATION_ID, buildNotification(cfg), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
                } else {
                    startForeground(NOTIFICATION_ID, buildNotification(cfg));
                }
                if (cfg.isPaused) {
                    cancelDone(this);
                } else if (cfg.isRunning && cfg.remainingMs > 0 && !"countup".equals(cfg.mode)) {
                    scheduleDone(this, System.currentTimeMillis() + cfg.remainingMs,
                            cfg.phase, cfg.soundMode, cfg.lang, null, null);
                }
            } else if (ACTION_STOP.equals(action)) {
                // JS 主动停止（stopTimerNotification）：闹钟由 JS 决定是否取消
                stopTicker();
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            } else if (ACTION_STOP_BTN.equals(action)) {
                // 通知栏停止按钮：先通知页面同步状态，再取消闹钟并停服务
                TimerNotifierPlugin.notifyTimerAction("stop");
                cancelDone(this);
                stopTicker();
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            } else if (ACTION_PAUSE_BTN.equals(action)) {
                TimerNotifierPlugin.notifyTimerAction("pause");
            } else if (ACTION_RESUME_BTN.equals(action)) {
                TimerNotifierPlugin.notifyTimerAction("resume");
            }
        } catch (Throwable t) {
            // 任何异常都不允许拖垮进程
        }
        return START_STICKY;
    }

    private void startTicker() {
        if (tickHandler == null) tickHandler = new Handler(Looper.getMainLooper());
        if (tickRunnable != null) tickHandler.removeCallbacks(tickRunnable);
        tickRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    Config c = lastCfg;
                    if (c == null || !c.isRunning || c.isPaused) return;
                    long elapsed = SystemClock.elapsedRealtime() - cfgStartElapsed;
                    if ("countup".equals(c.mode)) {
                        c.elapsedMs = elapsed;
                        c.contentText = ("en".equals(c.lang) ? "Elapsed " : "已计时 ") + fmtClock(elapsed);
                    } else {
                        long remaining = Math.max(0, cfgTotalMs - elapsed);
                        c.remainingMs = remaining;
                        c.contentText = ("en".equals(c.lang) ? "Remaining " : "剩余 ") + fmtClock(remaining);
                    }
                    NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    nm.notify(NOTIFICATION_ID, buildNotification(c));
                } catch (Throwable t) {
                    // 单次刷新失败忽略
                }
                if (tickHandler != null && tickRunnable != null) {
                    tickHandler.postDelayed(tickRunnable, 1000L);
                }
            }
        };
        tickHandler.postDelayed(tickRunnable, 0L);
    }

    private void stopTicker() {
        if (tickHandler != null && tickRunnable != null) {
            tickHandler.removeCallbacks(tickRunnable);
        }
        tickRunnable = null;
    }

    private static String fmtClock(long ms) {
        long totalSec = ms / 1000;
        long h = totalSec / 3600, m = (totalSec % 3600) / 60, s = totalSec % 60;
        return h > 0
                ? String.format(Locale.US, "%d:%02d:%02d", h, m, s)
                : String.format(Locale.US, "%02d:%02d", m, s);
    }

    private Notification buildNotification(Config cfg) {
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(cfg.title)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(contentIntent)
                .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        if (cfg.isRunning && !cfg.isPaused) {
            String bodyText = (cfg.contentText != null && cfg.contentText.length() > 0)
                    ? cfg.contentText : cfg.stateLabel;
            b.setContentText(bodyText);
        } else {
            b.setContentText(cfg.pausedText);
        }

        if (cfg.isRunning && !cfg.isPaused) {
            PendingIntent pausePi = PendingIntent.getService(this, 1,
                    new Intent(this, TimerForegroundService.class).setAction(ACTION_PAUSE_BTN),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            b.addAction(0, cfg.pauseLabel, pausePi);
        } else if (cfg.isPaused) {
            PendingIntent resumePi = PendingIntent.getService(this, 2,
                    new Intent(this, TimerForegroundService.class).setAction(ACTION_RESUME_BTN),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            b.addAction(0, cfg.resumeLabel, resumePi);
        }
        PendingIntent stopPi = PendingIntent.getService(this, 3,
                new Intent(this, TimerForegroundService.class).setAction(ACTION_STOP_BTN),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        b.addAction(0, cfg.stopLabel, stopPi);

        return b.build();
    }

    private static class Config {
        String title = "学习打卡";
        String mode = "pomodoro";
        String phase = "focus";
        String lang = "zh";
        String soundMode = "silent";
        String stateLabel = "专注中";
        String pauseLabel = "暂停";
        String resumeLabel = "继续";
        String stopLabel = "停止";
        boolean isRunning = true;
        boolean isPaused = false;
        long remainingMs = 0L;
        long elapsedMs = 0L;
        String pausedText = "已暂停";
        String contentText = null;
    }

    private Config parse(String raw) {
        JSONObject o;
        try {
            o = new JSONObject(raw == null ? "{}" : raw);
        } catch (Exception e) {
            o = new JSONObject();
        }
        Config c = new Config();
        c.title = o.optString("title", "学习打卡");
        c.mode = o.optString("mode", "pomodoro");
        c.phase = o.optString("phase", "focus");
        c.lang = o.optString("lang", "zh");
        c.soundMode = o.optString("soundMode", "silent");
        c.stateLabel = o.optString("stateLabel", "专注中");
        c.pauseLabel = o.optString("pauseLabel", "暂停");
        c.resumeLabel = o.optString("resumeLabel", "继续");
        c.stopLabel = o.optString("stopLabel", "停止");
        c.isRunning = o.optBoolean("running", true);
        c.isPaused = o.optBoolean("paused", false);
        c.remainingMs = o.optLong("remainingMs", 0L);
        c.elapsedMs = o.optLong("elapsedMs", 0L);
        c.pausedText = o.optString("pausedText", "已暂停");
        return c;
    }

}
