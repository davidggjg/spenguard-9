# אתר SPENguard

אתר סטטי המשרת את דרישות Google Play: מדיניות פרטיות, עמוד הורדה ועמוד תמיכה.

## הפעלת האתר (פעם אחת)

1. מזג את הענף הזה ל-`main`.
2. GitHub → **Settings → Pages**
3. תחת *Build and deployment*: Source = **Deploy from a branch**,
   Branch = **main**, Folder = **/docs** → Save
4. אחרי דקה האתר יעלה בכתובת:

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
