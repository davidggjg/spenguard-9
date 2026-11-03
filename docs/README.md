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

## לפני העלאה — שני ערכים להחלפה

```bash
# כתובת המייל לפניות (מופיעה במדיניות ובעמוד התמיכה)
grep -rl CONTACT_EMAIL_PLACEHOLDER docs PLAY_LISTING.md | \
  xargs sed -i 's/CONTACT_EMAIL_PLACEHOLDER/your@email.com/g'

# קישור לאפליקציה בחנות - רק אחרי שהיא תפורסם
grep -rl PLAY_STORE_URL_PLACEHOLDER docs PLAY_LISTING.md | \
  xargs sed -i 's#PLAY_STORE_URL_PLACEHOLDER#https://play.google.com/store/apps/details?id=com.davidggjg.spenguard#g'
```

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
