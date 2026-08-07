package com.study.pomodoro;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;

/**
 * JS <-> 原生通知桥：网页里的计时逻辑通过这个插件控制前台服务和准点闹钟。
 */
@CapacitorPlugin(name = "TimerNotifier", permissions = {
        @Permission(alias = "notifications", strings = {Manifest.permission.POST_NOTIFICATIONS})
})
public class TimerNotifierPlugin extends Plugin {

    private static TimerNotifierPlugin instance;

    public static void notifyTimerAction(String action) {
        try {
            TimerNotifierPlugin p = instance;
            if (p != null) {
                p.notifyListeners("timerAction", new JSObject().put("action", action));
            }
        } catch (Throwable t) {
            // 页面未就绪时忽略（如进程刚被拉起）
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
        if (canNotify()) {
            call.resolve(new JSObject().put("granted", true));
            return;
        }
        if (getActivity() == null) {
            call.resolve(new JSObject().put("granted", false));
            return;
        }
        // 弹出系统授权框，用户在对话框上做出选择后才 resolve
        requestPermissionForAlias("notifications", call, "permissionResult");
    }

    @ActivityCallback
    private void permissionResult(PluginCall call) {
        boolean granted = getPermissionState("notifications") == PermissionState.GRANTED;
        call.resolve(new JSObject().put("granted", granted));
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
