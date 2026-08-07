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
    '    <uses-permission android:name="android.permission.USE_EXACT_ALARM"/>\n'
)
COMPONENTS = (
    '        <service android:name=".TimerForegroundService" android:exported="false" '
    'android:foregroundServiceType="specialUse"/>\n'
    '        <receiver android:name=".TimerSoundReceiver" android:exported="false"/>\n'
)


def dump(path, s):
    print("---- " + path + " ----")
    print(s)
    print("---- end ----")


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
        dump(manifest, m)
        sys.exit(1)
    with open(manifest, "w", encoding="utf-8") as f:
        f.write(m2)
    print("OK manifest")


# 2) MainActivity：注册插件。
# Capacitor 要求 registerPlugin 在 super.onCreate 之前调用。
# 兼容两种模板：
#   - 旧模板：已有 onCreate 方法，直接插到 super.onCreate(savedInstanceState) 前
#   - Capacitor 7/8 新模板：空类体 public class MainActivity extends BridgeActivity {}
def patch_java(path):
    with open(path, encoding="utf-8") as f:
        s = f.read()
    marker = "registerPlugin(TimerNotifierPlugin.class);"
    if marker in s:
        print("OK MainActivity.java (already patched)")
        return
    if "class MainActivity" not in s:
        print("FAIL MainActivity.java: MainActivity class not found")
        dump(path, s)
        sys.exit(1)

    m = re.search(r"([ \t]*)super\.onCreate\(savedInstanceState\)", s)
    if m:
        s = s.replace(m.group(0), m.group(1) + marker + "\n" + m.group(0), 1)
    else:
        cls = re.search(r"\bclass\s+MainActivity\b[^{]*\{", s, re.S)
        if not cls:
            print("FAIL MainActivity.java: class body brace not found")
            dump(path, s)
            sys.exit(1)
        onCreate = (
            "\n\n    @Override\n"
            "    public void onCreate(Bundle savedInstanceState) {\n"
            "        " + marker + "\n"
            "        super.onCreate(savedInstanceState);\n"
            "    }\n"
        )
        s = s[:cls.end()] + onCreate + s[cls.end():]

    if "import android.os.Bundle;" not in s:
        if "import com.getcapacitor.BridgeActivity;" in s:
            s = s.replace(
                "import com.getcapacitor.BridgeActivity;",
                "import android.os.Bundle;\nimport com.getcapacitor.BridgeActivity;",
                1,
            )
        else:
            s = re.sub(r"(?m)^package [^\n]+\n", r"\g<0>\nimport android.os.Bundle;\n", s, count=1)
    with open(path, "w", encoding="utf-8") as f:
        f.write(s)
    print("OK MainActivity.java")


def patch_kt(path):
    with open(path, encoding="utf-8") as f:
        s = f.read()
    marker = "registerPlugin(TimerNotifierPlugin::class.java)"
    if marker in s:
        print("OK MainActivity.kt (already patched)")
        return
    if "class MainActivity" not in s:
        print("FAIL MainActivity.kt: MainActivity class not found")
        dump(path, s)
        sys.exit(1)

    m = re.search(r"([ \t]*)super\.onCreate\(savedInstanceState\)", s)
    if m:
        s = s.replace(m.group(0), m.group(1) + marker + "\n" + m.group(0), 1)
    else:
        cls = re.search(r"\bclass\s+MainActivity\b[^{]*\{", s, re.S)
        if cls:
            onCreate = (
                "\n\n    override fun onCreate(savedInstanceState: Bundle?) {\n"
                "        " + marker + "\n"
                "        super.onCreate(savedInstanceState)\n"
                "    }\n"
            )
            s = s[:cls.end()] + onCreate + s[cls.end():]
        else:
            cls = re.search(r"\bclass\s+MainActivity\b[^\n]*", s)
            if not cls:
                print("FAIL MainActivity.kt: class declaration not found")
                dump(path, s)
                sys.exit(1)
            onCreate = (
                " {\n"
                "    override fun onCreate(savedInstanceState: Bundle?) {\n"
                "        " + marker + "\n"
                "        super.onCreate(savedInstanceState)\n"
                "    }\n"
                "}\n"
            )
            s = s[:cls.end()] + onCreate + s[cls.end():]

    if "import android.os.Bundle" not in s:
        s = re.sub(r"(?m)^package [^\n]+\n", r"\g<0>\nimport android.os.Bundle\n", s, count=1)
    with open(path, "w", encoding="utf-8") as f:
        f.write(s)
    print("OK MainActivity.kt")


if os.path.exists(main_java):
    patch_java(main_java)
elif os.path.exists(main_kt):
    patch_kt(main_kt)
else:
    print("FAIL: MainActivity not found in:")
    print("  " + main_java)
    print("  " + main_kt)
    sys.exit(1)

print("patch done")
