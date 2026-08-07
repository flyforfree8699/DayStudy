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

# 1) AndroidManifest.xml: permissions + service + receiver
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

# 2) MainActivity: register plugin before super.onCreate
def patch_java():
    with open(main_java, encoding="utf-8") as f:
        s = f.read()
    if "registerPlugin(TimerNotifierPlugin.class)" in s:
        print("OK MainActivity.java (already patched)")
        return

    # Try multiple anchor patterns
    anchors = [
        "        super.onCreate(savedInstanceState);",
        "    super.onCreate(savedInstanceState);",
        "super.onCreate(savedInstanceState);",
    ]
    patched = False
    for anchor in anchors:
        if anchor in s:
            s = s.replace(anchor, "        registerPlugin(TimerNotifierPlugin.class);\n" + anchor, 1)
            patched = True
            break

    if not patched:
        # If no super.onCreate found, add the whole onCreate method
        # Find the class body and add onCreate
        if "class MainActivity" in s:
            s = re.sub(
                r'(class MainActivity\s+extends\s+\w+\s*\{)',
                r'\1\n    @Override\n    protected void onCreate(Bundle savedInstanceState) {\n'
                r'        registerPlugin(TimerNotifierPlugin.class);\n'
                r'        super.onCreate(savedInstanceState);\n'
                r'    }\n',
                s, count=1
            )
            patched = True

    if not patched:
        print("FAIL MainActivity.java: could not patch")
        sys.exit(1)

    with open(main_java, "w", encoding="utf-8") as f:
        f.write(s)
    print("OK MainActivity.java")

def patch_kotlin():
    with open(main_kt, encoding="utf-8") as f:
        s = f.read()
    if "registerPlugin(TimerNotifierPlugin" in s:
        print("OK MainActivity.kt (already patched)")
        return

    anchors = [
        "        super.onCreate(savedInstanceState)",
        "    super.onCreate(savedInstanceState)",
        "super.onCreate(savedInstanceState)",
    ]
    patched = False
    for anchor in anchors:
        if anchor in s:
            s = s.replace(anchor, "        registerPlugin(TimerNotifierPlugin::class.java)\n" + anchor, 1)
            patched = True
            break

    if not patched:
        # If no super.onCreate found, add the whole onCreate method
        if "class MainActivity" in s:
            s = re.sub(
                r'(class MainActivity\s*(:\s*\w+\(\))?\s*\{)',
                r'\1\n    override fun onCreate(savedInstanceState: Bundle?) {\n'
                r'        registerPlugin(TimerNotifierPlugin::class.java)\n'
                r'        super.onCreate(savedInstanceState)\n'
                r'    }\n',
                s, count=1
            )
            patched = True

    if not patched:
        print("FAIL MainActivity.kt: could not patch")
        sys.exit(1)

    with open(main_kt, "w", encoding="utf-8") as f:
        f.write(s)
    print("OK MainActivity.kt")

if os.path.exists(main_java):
    patch_java()
elif os.path.exists(main_kt):
    patch_kotlin()
else:
    print("FAIL: MainActivity not found")
    sys.exit(1)

print("patch done")
