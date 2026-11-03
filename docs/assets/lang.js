/* Language switcher: remembers the choice, and respects ?lang= / #en links
   so a Google reviewer can be sent straight to the English version. */
(function () {
  var KEY = 'spenguard-lang';
  var buttons = Array.prototype.slice.call(document.querySelectorAll('.langbar button'));
  if (!buttons.length) return;

  function apply(lang) {
    document.querySelectorAll('section[lang]').forEach(function (s) {
      s.hidden = (s.getAttribute('lang') !== lang);
    });
    buttons.forEach(function (b) {
      b.setAttribute('aria-pressed', String(b.dataset.lang === lang));
    });
    document.documentElement.setAttribute('lang', lang);
    document.documentElement.setAttribute('dir', lang === 'he' ? 'rtl' : 'ltr');
    try { localStorage.setItem(KEY, lang); } catch (e) { /* private mode */ }
  }

  function initial() {
    var q = new URLSearchParams(location.search).get('lang');
    if (q === 'he' || q === 'en') return q;
    var h = location.hash.replace('#', '');
    if (h === 'he' || h === 'en') return h;
    try {
      var saved = localStorage.getItem(KEY);
      if (saved === 'he' || saved === 'en') return saved;
    } catch (e) { /* private mode */ }
    return (navigator.language || '').toLowerCase().indexOf('he') === 0 ? 'he' : 'he';
  }

  buttons.forEach(function (b) {
    b.addEventListener('click', function () { apply(b.dataset.lang); });
  });

  apply(initial());
})();
