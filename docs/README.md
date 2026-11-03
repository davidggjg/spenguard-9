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
https://davidggjg.github.io/spenguard-9/
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

כתובת הקשר באתר היא `0555687549z@gmail.com`. להחלפה:

```bash
grep -rl '0555687549z@gmail.com' docs PLAY_LISTING.md | \
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
