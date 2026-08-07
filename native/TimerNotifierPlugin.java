package com.study.pomodoro;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * JS <-> 原生通知桥：网页里的计时逻辑通过这个插件控制前台服务和准点闹钟。
 */
@CapacitorPlugin(name = "TimerNotifier")
public class TimerNotifierPlugin extends Plugin {

    private static TimerNotifierPlugin instance;

    public static void notifyTimerAction(String action) {
        TimerNotifierPlugin p = instance;
        if (p != null) {
            p.notifyListeners("timerAction", new JSObject().put("action", action));
        }
    }

    @Override
    public void load() {
        super.load();
        instance = this;
        TimerForegroundService.ensureChannel(getContext());
    }

    private boolean canNotify() {
        if (Build.VERSION.SDK_INT < 33) return true;
        return ContextCompat.checkSelfPermission(getContext(), Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    @PluginMethod
    public void requestPermission(PluginCall call) {
        boolean ok = canNotify();
        if (!ok && getActivity() != null) {
            ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
        call.resolve(new JSObject().put("granted", ok));
    }

    @PluginMethod
    public void startForeground(PluginCall call) {
        if (!canNotify()) {
            call.reject("通知权限未授权");
            return;
        }
        TimerForegroundService.start(getContext(), call.getData());
        call.resolve();
    }

    @PluginMethod
    public void update(PluginCall call) {
        if (!canNotify()) {
            call.reject("通知权限未授权");
            return;
        }
        TimerForegroundService.start(getContext(), call.getData());
        call.resolve();
    }

    @PluginMethod
    public void stop(PluginCall call) {
        TimerForegroundService.stop(getContext());
        call.resolve();
    }

    @PluginMethod
    public void scheduleDone(PluginCall call) {
        Double at = call.getDouble("at", 0.0);
        String phase = call.getString("phase");
        String soundMode = call.getString("soundMode");
        String lang = call.getString("lang");
        String title = call.getString("title");
        String body = call.getString("body");
        TimerForegroundService.scheduleDone(
                getContext(), at.longValue(),
                phase == null ? "focus" : phase,
                soundMode == null ? "ring" : soundMode,
                lang == null ? "zh" : lang,
                title, body);
        call.resolve();
    }

    @PluginMethod
    public void cancelDone(PluginCall call) {
        TimerForegroundService.cancelDone(getContext());
        call.resolve();
    }
}
