import os
import re
import sys

root = sys.argv[1]
manifest = os.path.join(root, "app", "src", "main", "AndroidManifest.xml")
main_java = os.path.join(root, "app", "src", "main", "java", "com", "study", "pomodoro", "MainActivity.java")
main_kt = os.path.join(root, "app", "src", "main", "java", "com", "study", "pomodoro", "MainActivity.kt")

PERMS = (
    '    <uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>\n'
    '    <uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>\n'
    '    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE"/>\n'
    '    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM"/>\n'
)
COMPONENTS = (
    '        <service android:name=".TimerForegroundService" android:exported="false" '
    'android:foregroundServiceType="specialUse"/>\n'
    '        <receiver android:name=".TimerSoundReceiver" android:exported="false"/>\n'
)

# 1) AndroidManifest.xml：权限 + 服务 + 接收器
with open(manifest, encoding="utf-8") as f:
    m = f.read()
if "POST_NOTIFICATIONS" in m:
    print("OK manifest (already patched)")
else:
    m2 = re.sub(r"(\s*<application)", PERMS + r"\1", m, count=1)
    m2 = re.sub(r"(\s*</application>)", COMPONENTS + r"\1", m2, count=1)
    if m2 == m:
        print("FAIL manifest: anchors not found")
        sys.exit(1)
    with open(manifest, "w", encoding="utf-8") as f:
        f.write(m2)
    print("OK manifest")

# 2) MainActivity：注册插件（Capacitor 要求 registerPlugin 在 super.onCreate 之前）
if os.path.exists(main_java):
    with open(main_java, encoding="utf-8") as f:
        s = f.read()
    if "registerPlugin(TimerNotifierPlugin.class)" in s:
        print("OK MainActivity.java (already patched)")
    else:
        anchor = "        super.onCreate(savedInstanceState);"
        if anchor not in s:
            print("FAIL MainActivity.java: anchor not found")
            sys.exit(1)
        s = s.replace(anchor, "        registerPlugin(TimerNotifierPlugin.class);\n" + anchor, 1)
        with open(main_java, "w", encoding="utf-8") as f:
            f.write(s)
        print("OK MainActivity.java")
elif os.path.exists(main_kt):
    with open(main_kt, encoding="utf-8") as f:
        s = f.read()
    if "registerPlugin(TimerNotifierPlugin" in s:
        print("OK MainActivity.kt (already patched)")
    else:
        anchor = "        super.onCreate(savedInstanceState)"
        if anchor not in s:
            print("FAIL MainActivity.kt: anchor not found")
            sys.exit(1)
        s = s.replace(anchor, "        registerPlugin(TimerNotifierPlugin::class.java)\n" + anchor, 1)
        with open(main_kt, "w", encoding="utf-8") as f:
            f.write(s)
        print("OK MainActivity.kt")
else:
    print("FAIL: MainActivity not found")
    sys.exit(1)

print("patch done")
