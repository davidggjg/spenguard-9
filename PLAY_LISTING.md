# SPENguard — כל הטקסטים ל-Google Play Console

מסמך זה מרכז את כל מה ש-Play Console יבקש ממך. העתק-הדבק מכאן.

> **לפני הכול:** החלף בכל המסמך ובאתר את `david.batish1@gmail.com`
> בכתובת מייל אמיתית, ואת `PLAY_STORE_URL_PLACEHOLDER` בקישור לאפליקציה
> אחרי שתיווצר בחנות.

---

## 1. פרטי האפליקציה

| שדה | ערך |
|---|---|
| App name | `SPENguard` |
| Default language | עברית (he-IL) |
| App or game | App |
| Free or paid | Free |
| Package name | `com.davidggjg.spenguard` |
| Category | Tools |
| Tags | Security, Utilities |
| Contact email | `david.batish1@gmail.com` |
| Website | `https://spenguard.netlify.app/` |
| Privacy Policy URL | `https://spenguard.netlify.app/privacy.html` |

---

## 2. Short description (עד 80 תווים)

```
אזעקה וצילום אוטומטי ברגע שה-S Pen נשלף מהמכשיר
```

English:
```
Instant alarm and auto photo the moment your S Pen is pulled out
```

---

## 3. Full description (עד 4000 תווים)

```
SPENguard שומרת על ה-S Pen שלך.

ברגע שהעט נשלף מהמכשיר, SPENguard מפעילה אזעקה חזקה ומצלמת תמונה
במצלמה הקדמית — גם כשהמסך כבוי וגם כשהמכשיר במצב שקט.

■ היכולות

• זיהוי מיידי של שליפת ה-S Pen
• אזעקה בעוצמה מלאה, עוקפת מצב שקט
• צילום אוטומטי במצלמה הקדמית, נשמר ישירות לגלריה
• מסך "עצור אזעקה" שמופיע מעל מסך הנעילה
• פועלת גם כשהמסך כבוי
• מתחדשת אוטומטית אחרי הפעלה מחדש של המכשיר

■ איך זה עובד

SPENguard מזהה את פתיחת תפריט Air Command של סמסונג — האירוע שמתרחש
בדיוק כשהעט נשלף. זו הדרך האמינה ביותר לזהות שליפה, גם כשהמסך כבוי.

■ התקנה בשלושה שלבים

האפליקציה מדריכה אותך שלב אחר שלב:
1. הרשאת מצלמה — לצילום מי ששלף את העט
2. מנהל מכשיר — כדי שההגנה תמשיך לפעול כשהמסך כבוי
3. שירות נגישות — כדי לזהות את שליפת העט

■ פרטיות

התמונות נשמרות אך ורק במכשיר שלך, בתיקייה Pictures/SPENguard.
הן לעולם אינן נשלחות לשרת כלשהו. אין חשבונות משתמש ואין הרשמה.
האפליקציה מציגה מודעות באמצעות Google AdMob, שאוסף מידע על המכשיר
לצורך הצגתן. הפירוט המלא במדיניות הפרטיות.

■ דרישות

• מכשיר Samsung עם S Pen (Galaxy Note, S Ultra, Tab S ועוד)
• אנדרואיד 7.0 ומעלה
• תפריט Air Command פעיל במכשיר

■ חשוב לדעת

באנדרואיד 14 ומעלה המערכת מגבילה שימוש במצלמה מהרקע. במצבים מסוימים
האזעקה תפעל ללא צילום. זו מגבלת מערכת ההפעלה.

מדיניות פרטיות: https://spenguard.netlify.app/privacy.html
```

---

## 4. ⚠️ שלוש ההצהרות הרגישות

אלה נבדקות **ידנית** על ידי Google. זה החלק שהכי מפיל אפליקציות כמו שלך.

### 4.1 Accessibility Service — טופס Permissions declaration

**שאלה: What is the core functionality of your app?**
```
SPENguard is a security app for Samsung devices with an S Pen. It detects
when the S Pen is physically removed from the device and immediately
sounds an alarm and takes a front-camera photo, so the owner knows the pen
was taken and has a picture of who took it.
```

**שאלה: Which accessibility capabilities does your app use, and why?**
```
The app uses AccessibilityService solely as a hardware-event detector.

Samsung's Air Command menu opens automatically whenever the S Pen is
detached from the device. Observing that this specific window opened is
the only reliable way to detect pen removal, because the Samsung S Pen
detach broadcast is not delivered consistently across firmware versions,
and is often not delivered at all while the screen is off — which is
exactly when the app must work.

The service is scoped as narrowly as the platform allows:
- android:packageNames is restricted to
  "com.samsung.android.service.aircommand" only, so the service receives
  no events from any other app.
- It reacts only to TYPE_WINDOW_STATE_CHANGED and TYPE_WINDOWS_CHANGED,
  and reads only the package name of the window that opened.
- It does not read screen text, does not record keystrokes, does not
  collect form data and does not perform any gestures or actions.
- No data obtained through the accessibility API is stored, logged
  persistently, or transmitted off the device.

There is no alternative API for this. Neither the public Android SDK nor
the Samsung SDK exposes S Pen attach/detach state to a third-party app.

The source code is public and can be verified at
https://github.com/davidggjg/spenguard-9
```

**שאלה: Is the use of the accessibility API disclosed to the user?**
```
Yes. The in-app setup wizard explains, before requesting it, that the
accessibility service is used to detect S Pen removal. The service's own
description shown in Android's accessibility settings states the same.
The privacy policy documents it in full in section 3.
```

### 4.2 Device Admin

```
SPENguard declares a DeviceAdminReceiver with only the force-lock and
watch-login policies. It does so purely so that Android treats the app as
an active administrator and does not terminate its foreground protection
service while the screen is off.

The app does not lock the device, does not wipe data, does not enforce
passwords and does not manage the device in any way. The
onPasswordFailed callback is intentionally empty. No device-admin API is
ever invoked to act on the device.
```

### 4.3 Camera / Photo and video permissions

```
The CAMERA permission is used for exactly one action: when the S Pen is
removed, the app captures a single still image with the front camera, so
the owner can see who took the pen.

The photo is written to the device's own gallery under
Pictures/SPENguard using MediaStore. It is never uploaded, never
transmitted and never shared with the developer or any third party. The
app has no server and no account system.

The app requests no photo- or video-reading permission; it only writes
the image it just captured.
```

---

## 5. Data safety — תשובות לטופס

| שאלה | תשובה |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** (בגלל AdMob) |
| Is all user data encrypted in transit? | **Yes** |
| Do you provide a way for users to request data deletion? | **No accounts** — אין מידע בצד שרת. ציין את הוראות המחיקה המקומיות |

**סוגי מידע שיש לסמן (כולם מגיעים מ-AdMob, לא מהאפליקציה עצמה):**

| קטגוריה | סוג | נאסף | משותף | מטרה |
|---|---|---|---|---|
| Device or other IDs | Device or other IDs | ✔ | ✔ | Advertising or marketing |
| App activity | App interactions | ✔ | ✔ | Advertising or marketing |
| App info and performance | Diagnostics | ✔ | ✔ | Analytics |
| Location | Approximate location | ✔ | ✔ | Advertising (נגזר מ-IP) |

> **אל תסמן "Photos"**. התמונה שהאפליקציה מצלמת נשמרת רק במכשיר ואינה
> נאספת ואינה משותפת — לפי ההגדרה של Google זה אינו "collection".

---

## 6. Content rating — תשובות

- אלימות: לא · תוכן מיני: לא · שפה גסה: לא · חומרים מבוקרים: לא · הימורים: לא
- **האפליקציה מציגה מודעות: כן**
- שיתוף מיקום עם משתמשים אחרים: לא
- תוכן שנוצר על ידי משתמשים: לא

## 7. Target audience

- **18 ומעלה בלבד.** אל תסמן קבוצות גיל של ילדים — האפליקציה משתמשת
  במצלמה, בנגישות ובמנהל מכשיר, וסימון ילדים יפעיל את מדיניות
  Families ויגרור דחייה כמעט ודאית.

## 8. App access

- `All functionality is available without special access` — אין התחברות.

## 9. Ads

- **Yes, my app contains ads.**

### הסכמת GDPR למודעות — מה לעשות ב-AdMob

האפליקציה כבר כוללת את מנגנון ההסכמה של Google (UMP) בקוד. כדי שהטופס
באמת יוצג, יש להגדיר אותו פעם אחת בקונסול של AdMob:

1. היכנס ל-[AdMob](https://apps.admob.com) → **Privacy & messaging**
2. בחר **GDPR** → **Create message**
3. בחר את האפליקציה SPENguard
4. הדבק את כתובת מדיניות הפרטיות:
   `https://spenguard.netlify.app/privacy.html`
5. בחר שפות: עברית ואנגלית
6. **Publish**

מומלץ ליצור גם הודעת **US states** (CCPA) באותו מקום — לא חובה, אבל
Google ממליצה.

> בלי שלב זה Google עלולה להפסיק להגיש מודעות למשתמשים באירופה.
> הקוד באפליקציה מוכן; חסרה רק ההגדרה בקונסול.

---

## 10. נכסים גרפיים

כל הנכסים מוכנים בתיקייה `play-assets/`:

| נכס | קובץ | גודל |
|---|---|---|
| App icon | `play-store-icon-512.png` | 512×512 |
| Feature graphic | `feature-graphic-1024x500.png` | 1024×500 |
| צילום מסך 1 | `screenshot-01-protection-active.png` | 1080×1920 |
| צילום מסך 2 | `screenshot-02-setup-step-1.png` | 1080×1920 |
| צילום מסך 3 | `screenshot-03-setup-step-3.png` | 1080×1920 |
| צילום מסך 4 | `screenshot-04-alarm-triggered.png` | 1080×1920 |

צילומי המסך הם שחזור מדויק של המסכים האמיתיים — כל צבע, גודל, מרווח
וטקסט נלקחו ישירות מקובצי ה-layout ומ-`MainActivity`, ועובדו ברזולוציית
xxhdpi אמיתית. באנר הפרסומת הושמט בכוונה: Google ממליצה לא להציג מודעות
בצילומי מסך בחנות.

> אם נוח לך לצלם מהמכשיר — עדיף תמיד. אלה קיימים כדי שתוכל להשלים את
> הדף בלי זה.

ליצירה מחדש: `cd play-assets/generator && node render.js`

---

## 11. סדר הפעולות המומלץ

1. החלף `david.batish1@gmail.com` בכל הקבצים
2. הפעל GitHub Pages → קבל את ה-URL של מדיניות הפרטיות
3. צור את האפליקציה ב-Play Console ומלא את סעיפים 1–9 כאן
4. העלה את ה-AAB ל-**Internal testing** (לא Production)
5. בדוק על מכשיר אמיתי
6. הוסף 20 בודקים ל-14 יום (נדרש לחשבונות אישיים חדשים)
7. רק אז — Production
