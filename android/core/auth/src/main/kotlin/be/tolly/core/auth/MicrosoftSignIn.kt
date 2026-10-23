package be.tolly.core.auth

import android.net.Uri

/** Microsoft's sign-in, which Outlook goes through before handing over to the KU Leuven one. */
object MicrosoftSignIn {
    private const val HOST = "login.microsoftonline.com"

    fun isSignIn(url: String?): Boolean = url?.let { Uri.parse(it).host } == HOST

    /**
     * Answers Microsoft's "Stay signed in?" with "Don't show this again" ticked and Yes, so Outlook
     * keeps its session and the question doesn't come back. The page is found by the ids Microsoft
     * gives that form; it is also caught when the page builds it after loading. Nothing else is
     * touched, and the question is answered only once per page.
     */
    const val STAY_SIGNED_IN = """
(function () {
  if (window.__tollyKmsi) return;
  window.__tollyKmsi = true;
  function apply() {
    var box = document.getElementById('KmsiCheckboxField');
    var yes = document.getElementById('idSIButton9');
    if (!box || !yes || window.__tollyKmsiDone) return false;
    window.__tollyKmsiDone = true;
    if (!box.checked) box.click();
    yes.click();
    return true;
  }
  if (apply()) return;
  var observer = new MutationObserver(function () { if (apply()) observer.disconnect(); });
  observer.observe(document.documentElement, { childList: true, subtree: true });
})();
"""
}
