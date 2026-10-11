package net.box86.android;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.app.ApplicationExitInfo;
import android.content.ComponentCallbacks2;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;

import java.util.List;

/** Process-wide lifecycle recorder used by the v0.9.7 complete app diagnostics. */
public final class Box86Application extends Application implements Application.ActivityLifecycleCallbacks, ComponentCallbacks2 {
    private static final String TAG = "AppLifecycle";

    @Override
    public void onCreate() {
        super.onCreate();
        DiagnosticLogger.startAppSession(this);
        DiagnosticLogger.i(TAG, "Application.onCreate pid=" + android.os.Process.myPid());
        logRecentProcessExitReasons();
        registerActivityLifecycleCallbacks(this);
        registerComponentCallbacks(this);
    }


    private void logRecentProcessExitReasons() {
        if (Build.VERSION.SDK_INT < 30)
            return;
        try {
            ActivityManager am = getSystemService(ActivityManager.class);
            if (am == null)
                return;
            List<ApplicationExitInfo> exits = am.getHistoricalProcessExitReasons(getPackageName(), 0, 4);
            int shown = 0;
            for (ApplicationExitInfo info : exits) {
                if (info == null)
                    continue;
                DiagnosticLogger.w(TAG, "Previous process exit: reason=" + exitReasonName(info.getReason()) +
                        "(" + info.getReason() + ") status=" + info.getStatus() +
                        " importance=" + info.getImportance() +
                        " pss=" + info.getPss() + "KB rss=" + info.getRss() + "KB" +
                        " timestamp=" + info.getTimestamp() +
                        " description=" + String.valueOf(info.getDescription()));
                if (++shown >= 3)
                    break;
            }
        } catch (Throwable t) {
            DiagnosticLogger.w(TAG, "Unable to query previous process exit reasons", t);
        }
    }

    private static String exitReasonName(int reason) {
        switch (reason) {
            case ApplicationExitInfo.REASON_ANR: return "ANR";
            case ApplicationExitInfo.REASON_CRASH: return "CRASH";
            case ApplicationExitInfo.REASON_CRASH_NATIVE: return "CRASH_NATIVE";
            case ApplicationExitInfo.REASON_DEPENDENCY_DIED: return "DEPENDENCY_DIED";
            case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE: return "EXCESSIVE_RESOURCE_USAGE";
            case ApplicationExitInfo.REASON_EXIT_SELF: return "EXIT_SELF";
            case ApplicationExitInfo.REASON_INITIALIZATION_FAILURE: return "INITIALIZATION_FAILURE";
            case ApplicationExitInfo.REASON_LOW_MEMORY: return "LOW_MEMORY";
            case ApplicationExitInfo.REASON_OTHER: return "OTHER";
            case ApplicationExitInfo.REASON_PERMISSION_CHANGE: return "PERMISSION_CHANGE";
            case ApplicationExitInfo.REASON_SIGNALED: return "SIGNALED";
            case ApplicationExitInfo.REASON_USER_REQUESTED: return "USER_REQUESTED";
            case ApplicationExitInfo.REASON_USER_STOPPED: return "USER_STOPPED";
            default: return "UNKNOWN";
        }
    }

    private void lifecycle(String event, Activity activity) {
        String name = activity == null ? "null" : activity.getClass().getSimpleName();
        StringBuilder msg = new StringBuilder(event).append(' ').append(name);
        if (activity != null) {
            Intent i = activity.getIntent();
            if (i != null) {
                String profileName = i.getStringExtra(ProfileManagerActivity.EXTRA_PROFILE_NAME);
                String profilePath = i.getStringExtra(ProfileManagerActivity.EXTRA_PROFILE_PATH);
                if (profileName != null) msg.append(" profileName=").append(profileName);
                if (profilePath != null) msg.append(" profilePath=").append(profilePath);
            }
        }
        DiagnosticLogger.i(TAG, msg.toString());
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) { lifecycle("CREATED", activity); }
    @Override public void onActivityStarted(Activity activity) { lifecycle("STARTED", activity); }
    @Override public void onActivityResumed(Activity activity) { lifecycle("RESUMED", activity); }
    @Override public void onActivityPaused(Activity activity) { lifecycle("PAUSED", activity); }
    @Override public void onActivityStopped(Activity activity) { lifecycle("STOPPED", activity); }
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { lifecycle("SAVE_STATE", activity); }
    @Override public void onActivityDestroyed(Activity activity) { lifecycle("DESTROYED", activity); }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        DiagnosticLogger.i(TAG, "Application configuration changed orientation=" + newConfig.orientation + " uiMode=" + newConfig.uiMode);
    }

    @Override public void onLowMemory() { DiagnosticLogger.w(TAG, "Application.onLowMemory"); }

    @Override public void onTrimMemory(int level) { DiagnosticLogger.w(TAG, "Application.onTrimMemory level=" + level); }
}
