# SPENguard

אפליקציית אנדרואיד שמזהה שליפה של ה-**S Pen** ממכשיר Samsung, מפעילה אזעקה,
מצלמת תמונה עם המצלמה הקדמית ושומרת אותה לגלריה — גם כשהמסך כבוי.

| פריט | ערך |
|---|---|
| `applicationId` | `com.davidggjg.spenguard` |
| `minSdk` | 24 (Android 7.0) |
| `targetSdk` / `compileSdk` | 36 (Android 16) |
| `versionCode` / `versionName` | 1 / `1.0.0` |
| שפה | Java |
| Gradle / AGP | 8.14.3 / 8.11.1 |
| JDK לבנייה | 17 |

---

## 1. בנייה מהירה

```bash
git clone https://github.com/davidggjg/spenguard-9.git
cd spenguard-9
chmod +x gradlew
```

נדרשים **JDK 17** ו-**Android SDK** (משתנה הסביבה `ANDROID_HOME` או קובץ
`local.properties` עם `sdk.dir=...`).

### א. Debug APK — להתקנה ידנית ולבדיקות

```bash
./gradlew clean spenguardDebugApk
```

הקובץ נוצר ב:

```
app/build/outputs/dist/SPENguard-debug.apk
```

לא נדרש מפתח חתימה — Gradle חותם אוטומטית במפתח ה-debug.
התקנה למכשיר מחובר:

```bash
adb install -r app/build/outputs/dist/SPENguard-debug.apk
```

### ב. Release APK — חתום, להתקנה ידנית לפני הפצה

```bash
./gradlew clean spenguardReleaseApk
```

```
app/build/outputs/dist/SPENguard-release.apk
```

### ג. Release AAB — הפורמט להעלאה ל-Google Play

```bash
./gradlew clean spenguardReleaseAab
```

```
app/build/outputs/dist/SPENguard-release.aab
```

> משימות ה-`spenguard…` מריצות את `assemble`/`bundle` הרגילות ואז מעתיקות את
> הפלט ל-`app/build/outputs/dist/` בשם ברור. הקבצים המקוריים של Gradle
> (`app-debug.apk` וכו') נשארים במקומם הרגיל.
>
> `./gradlew spenguardAll` בונה את שלושת הקבצים בפקודה אחת.

ב-ב' ו-ג' חובה שיהיה מוגדר מפתח חתימה (ראו סעיף 2). **אם אין מפתח מוגדר
הבנייה לא נכשלת — היא מפיקה פלט לא חתום ומדפיסה אזהרה.** פלט לא חתום לא
ניתן להתקנה ולא ניתן להעלאה ל-Google Play.

---

## 2. הגדרת מפתח חתימה (Signing)

> ⚠️ **אסור לשמור מפתחות או סיסמאות ב-git.** `keystore.properties`,
> `*.jks` ו-`*.keystore` כבר נמצאים ב-`.gitignore`.
> אם תאבד את המפתח לא תוכל לפרסם עדכונים לאפליקציה שכבר עלתה לחנות —
> שמור גיבוי במקום מאובטח.

### יצירת מפתח חדש (פעם אחת)

```bash
keytool -genkeypair -v \
  -keystore spenguard-release.keystore \
  -alias spenguard \
  -keyalg RSA -keysize 2048 -validity 10000
```

### הגדרה לבנייה מקומית

העתק את `keystore.properties.example` ל-`keystore.properties` בשורש הפרויקט
ומלא את הערכים:

```properties
storeFile=spenguard-release.keystore
storePassword=הסיסמה-שלך
keyAlias=spenguard
keyPassword=הסיסמה-שלך
```

לחלופין אפשר להשתמש במשתני סביבה במקום בקובץ:

```bash
export KEYSTORE_FILE=/path/to/spenguard-release.keystore
export KEYSTORE_PASSWORD=...
export KEY_ALIAS=spenguard
export KEY_PASSWORD=...
```

### הגדרה ל-GitHub Actions

ב-**Settings → Secrets and variables → Actions** הגדר ארבעה secrets:

| Secret | תוכן |
|---|---|
| `KEYSTORE_BASE64` | הקובץ `.keystore` מקודד ב-base64 |
| `KEYSTORE_PASSWORD` | סיסמת ה-keystore |
| `KEY_ALIAS` | שם ה-alias (למשל `spenguard`) |
| `KEY_PASSWORD` | סיסמת המפתח |

יצירת ה-base64:

```bash
base64 -w0 spenguard-release.keystore > keystore.b64   # Linux
base64 -i spenguard-release.keystore | tr -d '\n'      # macOS
```

---

## 3. בנייה אוטומטית ב-GitHub Actions

ה-workflow נמצא ב-[`.github/workflows/build.yml`](.github/workflows/build.yml)
ורץ בכל push, בכל pull request ובהפעלה ידנית (**Actions → Build SPENguard →
Run workflow**).

מה הוא עושה:

1. מתקין JDK 17 + Android SDK.
2. `gradle clean`.
3. מריץ בדיקות יחידה (`testDebugUnitTest`).
4. מריץ Android Lint (`lintDebug`).
5. בונה **Debug APK** ומוודא שהקובץ באמת נוצר.
6. אם מוגדרים secrets של חתימה — בונה **Release APK** ו-**Release AAB**,
   ומאמת את החתימה בפועל עם `apksigner` ו-`jarsigner`.
7. מעלה את הקבצים כ-Artifacts ומסכם הצלחה/כישלון ב-Job Summary.

אם קובץ פלט לא נוצר — ה-workflow נכשל במפורש ולא מדווח על הצלחה.

**הורדת הקבצים:** Actions → בוחרים את ה-run → בתחתית העמוד, תחת *Artifacts*:

- `SPENguard-debug-apk-<run>` 
- `SPENguard-release-apk-<run>` 
- `SPENguard-release-aab-<run>` 
- `SPENguard-reports-<run>` — דוחות Lint ובדיקות

---

## 4. העלאה ל-Google Play Console

1. היכנס ל-[Play Console](https://play.google.com/console) → בחר/צור את האפליקציה.
2. **Release → Production** (או Internal testing לבדיקה) → **Create new release**.
3. העלה את `SPENguard-release.aab`.
4. מומלץ להשאיר את **Play App Signing** מופעל; המפתח שלך משמש כ-upload key.
5. מלא את Store listing: שם, תיאור, צילומי מסך, ואייקון 512×512 —
   קיים כבר ב-[`play-assets/play-store-icon-512.png`](play-assets/play-store-icon-512.png).
6. מלא את **Data safety** ואת שאלון התוכן.

### הצהרות נדרשות בגלל ההרשאות הרגישות

האפליקציה משתמשת בשלוש יכולות ש-Google בודקת ידנית. יש להסביר אותן
ב-Play Console, אחרת הגרסה תידחה:

| יכולת | למה היא נחוצה | מה למלא |
|---|---|---|
| **Accessibility Service** | זיהוי פתיחת Air Command של סמסונג — האירוע שמסמן ששלפו את ה-S Pen | טופס *Permissions declaration* + הצהרת שימוש בנגישות |
| **Device Admin** | שמירה על פעילות השירות כשהמסך כבוי (`force-lock`, `watch-login`) | הסבר בטופס ההצהרות |
| **Camera** | צילום של מי ששלף את העט | תיאור בשימוש במצלמה + מדיניות פרטיות |

נדרשת גם **מדיניות פרטיות** פומבית (URL) שמסבירה שהתמונה נשמרת מקומית
בגלריה של המכשיר בלבד ואינה נשלחת לשרת.

### הפקת עדכון (גרסה חדשה)

ב-[`app/build.gradle`](app/build.gradle) עדכן:

```groovy
versionCode 2          // חייב לעלות בכל העלאה לחנות
versionName "1.0.1"    // הגרסה שהמשתמש רואה
```

ואז `./gradlew clean spenguardReleaseAab` והעלאה של ה-AAB החדש.
חשוב לחתום עם **אותו** מפתח כמו בגרסה הקודמת.

---

## 5. מבנה הפרויקט

```
app/src/main/java/com/davidggjg/spenguard/
├── ui/MainActivity.java              אשף ההגדרה (3 שלבים) ומתג הפעלה
├── ui/StopAlarmActivity.java         מסך "עצור אזעקה" מעל מסך הנעילה
├── receiver/SPenReceiver.java        שידורי S Pen של סמסונג + BOOT_COMPLETED
├── receiver/SPenDeviceAdminReceiver.java
├── service/WatchdogService.java      foreground service שמחזיק את ה-receivers
├── service/SPenGuardService.java     אזעקה + צילום + שמירה לגלריה
├── service/SPenAccessibilityService.java  זיהוי דרך Air Command
└── util/ServiceUtils.java            הפעלת שירותים בלי קריסות
```

---

## 6. פתרון תקלות

| תופעה | פתרון |
|---|---|
| `SDK location not found` | הגדר `ANDROID_HOME` או צור `local.properties` עם `sdk.dir=/path/to/Android/sdk` |
| בנייה מפיקה APK לא חתום | לא נמצאו פרטי חתימה — ראה סעיף 2 |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | מותקנת גרסה חתומה במפתח אחר: `adb uninstall com.davidggjg.spenguard` |
| האזעקה לא מופעלת | ודא ששלושת השלבים באפליקציה הושלמו, ובטל אופטימיזציית סוללה לאפליקציה |
