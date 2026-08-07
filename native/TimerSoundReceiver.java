package com.study.pomodoro;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

/**
 * 准点完成闹钟的接收器（对应 Timety 的 TimerSoundReceiver + AlarmReceiver）：
 * 到点弹一条会响铃的完成通知，锁屏/后台/App 被关都有效。
 */
public class TimerSoundReceiver extends BroadcastReceiver {

    public static final String ACTION_DONE = "com.study.pomodoro.DONE";
    public static final String EXTRA_PHASE = "phase";
    public static final String EXTRA_SOUND_MODE = "soundMode";
    public static final String EXTRA_LANG = "lang";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_BODY = "body";
    public static final String DONE_CHANNEL_ID = "study_timer_done";
    public static final int DONE_NOTIFICATION_ID = 2002;

    @Override
    public void onReceive(Context context, Intent intent) {
        String phase = intent.getStringExtra(EXTRA_PHASE);
        if (phase == null) phase = "focus";
        String soundMode = intent.getStringExtra(EXTRA_SOUND_MODE);
        if (soundMode == null) soundMode = "ring";
        String lang = intent.getStringExtra(EXTRA_LANG);
        String title = intent.getStringExtra(EXTRA_TITLE);
        if (title == null) title = "en".equals(lang) ? "Timer finished" : "计时完成";
        String body = intent.getStringExtra(EXTRA_BODY);
        if (body == null) {
            boolean isBreak = "shortBreak".equals(phase) || "longBreak".equals(phase);
            body = "en".equals(lang) ? (isBreak ? "Break finished" : "Focus finished")
                    : (isBreak ? "休息结束" : "专注结束");
        }

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = DONE_CHANNEL_ID + "_" + soundMode;
        if (nm.getNotificationChannel(channelId) == null) {
            int importance;
            if ("silent".equals(soundMode)) importance = NotificationManager.IMPORTANCE_LOW;
            else if ("vibrate".equals(soundMode)) importance = NotificationManager.IMPORTANCE_DEFAULT;
            else importance = NotificationManager.IMPORTANCE_HIGH;
            String channelName = "en".equals(lang) ? "Timer alert" : "计时完成";
            NotificationChannel ch = new NotificationChannel(channelId, channelName, importance);
            ch.setDescription("en".equals(lang) ? "Timer alert" : "计时结束时提醒");
            if ("vibrate".equals(soundMode)) {
                ch.setSound(null, null);
                ch.enableVibration(true);
                ch.setVibrationPattern(new long[]{0, 300, 200, 300});
            }
            nm.createNotificationChannel(ch);
        }

        PendingIntent contentIntent = PendingIntent.getActivity(context, 0,
                new Intent(context, MainActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        try {
            NotificationManagerCompat.from(context).notify(DONE_NOTIFICATION_ID, b.build());
        } catch (SecurityException e) {
            // 通知权限被关闭时静默
        }
    }
}
