# אתר SPENguard

אתר סטטי המשרת את דרישות Google Play: מדיניות פרטיות, עמוד הורדה ועמוד תמיכה.

## הפעלת האתר (פעם אחת, ~20 שניות)

הקוד כבר נמצא ב-`main`. נותר רק להדליק את Pages — זו הפעולה היחידה
שחייבת להיעשות ידנית, כי GitHub אינו מתיר ל-workflow ליצור אתר Pages.

1. GitHub → **Settings → Pages**
2. תחת *Build and deployment*: Source = **Deploy from a branch**
3. Branch = **main**, Folder = **/docs** → **Save**
4. אחרי דקה-שתיים האתר יעלה בכתובת:

```
https://spenguard.netlify.app/
```

| עמוד | כתובת |
|---|---|
| בית | `/` |
| **מדיניות פרטיות** | `/privacy.html` ← זה מה שמזינים ב-Play Console |
| הורדה | `/download.html` |
| תמיכה | `/support.html` |

הוספת `?lang=en` לכל כתובת פותחת אותה באנגלית — שימושי אם בודק מטעם
Google מבקש גרסה אנגלית.

> אם תעדיף את מקור ה-**GitHub Actions** במקום *Deploy from a branch*,
> בחר אותו ב-Settings → Pages והרץ ידנית את ה-workflow
> *Deploy website*. שתי הדרכים מפיקות את אותו אתר; דרך הענף פשוטה יותר.

## אינדוקס בגוגל

אחרי שהאתר עולה:

1. היכנס ל-[Google Search Console](https://search.google.com/search-console)
2. הוסף נכס מסוג **URL prefix** עם הכתובת של האתר
3. אימות: בחר **HTML tag**, והוסף את תג האימות ל-`docs/index.html`
   (בתוך ה-`<head>`), דחוף ל-`main` והמתן שהאתר יתעדכן
4. תחת *Sitemaps*, הזן `sitemap.xml` ← Submit
5. תחת *URL Inspection*, הדבק את כתובת מדיניות הפרטיות ולחץ
   **Request indexing**

האתר כבר כולל `sitemap.xml`, `robots.txt`, תגי canonical ו-Open Graph,
ו-JSON-LD בעמוד הבית.

## החלפת כתובת המייל

כתובת הקשר באתר היא `david.batish1@gmail.com`. להחלפה:

```bash
grep -rl 'david.batish1@gmail.com' docs PLAY_LISTING.md | \
  xargs sed -i 's/0555687549z@gmail\.com/NEW@EMAIL.COM/g'
```

## הפעלת כפתור Google Play

כפתור החנות בעמוד ההורדה מוסתר עד לפרסום האפליקציה. כדי להפעיל אותו,
מחק את השורות `<!-- PLAY:START -->` ו-`<!-- PLAY:END -->` שעוטפות אותו
ב-`docs/download.html` (בשתי השפות).

## קישורי ההורדה

עמוד ההורדה מפנה ל-GitHub Releases. כדי שהקישורים יעבדו, פרסם גרסה:

```bash
git tag v1.0.0
git push origin v1.0.0
```

ה-workflow שב-`.github/workflows/release.yml` יבנה APK ו-AAB חתומים
ויצרף אותם ל-Release אוטומטית.

## עדכון תאריך המדיניות

אם תשנה את תוכן המדיניות, עדכן את שתי השורות של התאריך ב-`privacy.html`
(בגרסה העברית ובאנגלית).


---

# העברת האתר לאחסון אחר

## ⚠️ למה זה דחוף אם המאגר הופך לפרטי

**GitHub Pages אינו עובד ממאגר פרטי בחשבון חינמי** (נדרש GitHub Pro).
ברגע שהמאגר ייסגר, האתר ייפול — ואיתו כתובת מדיניות הפרטיות הרשומה
ב-Google Play. Google סורקת את הקישור הזה, ואם הוא מחזיר 404 האפליקציה
עלולה להיות מוסרת מהחנות.

**לכן הסדר הנכון הוא:**

1. להעלות את האתר לאחסון החדש
2. לוודא שהוא חי
3. לעדכן את הכתובת ב-Play Console
4. **ורק אז** לסגור את המאגר

לעולם לא לסגור את המאגר לפני שלב 3.

## שלב 1 — העלאה (Netlify)

1. היכנס ל-[netlify.com](https://www.netlify.com) והירשם עם חשבון GitHub
2. **Add new site → Import an existing project → GitHub**
3. בחר את המאגר `spenguard-9`
4. ההגדרות נקראות אוטומטית מ-`netlify.toml`:
   - Publish directory: `docs`
   - Build command: ריק
5. **Deploy**

תקבל כתובת זמנית כמו `https://random-name-123.netlify.app`.
ב-**Site configuration → Change site name** אפשר לשנות אותה למשל
ל-`spenguard`, ואז הכתובת תהיה `https://spenguard.netlify.app`.

> Netlify בתוכנית החינמית מתחבר גם למאגר פרטי — בדיוק מה שנדרש כאן.

### אותו דבר ב-Cloudflare Pages

אם תעדיף Cloudflare Pages, התהליך זהה: Connect to Git → בחר מאגר →
*Build output directory* = `docs`, *Build command* ריק. גם `_headers`
עובד שם.

## שלב 2 — דומיין משלך (אופציונלי)

ב-Netlify: **Domain management → Add a domain**, ואז להצביע את רשומות
ה-DNS אצל רשם הדומיין לפי ההוראות שיוצגו. תעודת HTTPS מונפקת אוטומטית.

## שלב 3 — עדכון הכתובות באתר

הקישורים בין העמודים יחסיים ועובדים בכל מקום. מה שצריך עדכון הוא
הכתובות המוחלטות (canonical, Open Graph, JSON-LD, sitemap, robots).
פקודה אחת עושה הכול:

```bash
./scripts/set-site-url.sh https://spenguard.netlify.app
# או, עם דומיין משלך:
./scripts/set-site-url.sh https://spenguard.com
```

הסקריפט מזהה בעצמו את הכתובת הנוכחית, מחליף אותה בכל הקבצים ומדפיס
מה השתנה. אחר כך:

```bash
git add -A && git commit -m "Point the site at the new host" && git push
```

## שלב 4 — עדכון השירותים החיצוניים

| איפה | מה לעדכן |
|---|---|
| **Play Console** | App content → Privacy policy → הכתובת החדשה |
| **AdMob** | Privacy & messaging → הודעת GDPR → קישור המדיניות |
| **Search Console** | להוסיף נכס חדש לדומיין החדש ולאמת מחדש |

> קובץ האימות של Search Console (`googleda24c725d544aa71.html`) נמצא
> בתוך `docs/` ולכן יעבור עם האתר אוטומטית.

## שלב 5 — כיבוי GitHub Pages

רק אחרי שהכול עובד: **Settings → Pages → Source: None**.
