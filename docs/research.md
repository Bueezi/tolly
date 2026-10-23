# Toledo Android client: Phase 1 research

Research date: 2026-10-18 (academic year 2026–27, course code suffix `2627`).
Method: read-only inspection of the logged-in web app in a browser. I only made GET requests, plus `HEAD` and ranged `GET` probes on one small file download. Nothing was submitted, posted or changed. One side effect to note: loading the Ultra course list page makes the Ultra UI itself send a `PUT` that saves the course-list term filter preference. That is ordinary page behaviour that happens on every visit, not something I triggered.

---

## 1. TL;DR

* "Toledo" is two systems:
  1. **Toledo Portal** (`https://toledo.kuleuven.be/portal`) is a KU Leuven-built Vue SPA with a Spring Boot JSON API. It shows the landing page with enrollments, the updates feed, upcoming lessons and links.
  2. **Blackboard Learn Ultra SaaS** (`https://ultra.edu.kuleuven.cloud`, Learn `4001.2.0`, build `rel.68`, EU-Central) is where the actual courses live: content, announcements, grades, calendar, discussions and so on.
* **A usable JSON API exists, so we don't need HTML scraping.** Blackboard's documented public REST API (`/learn/api/public/v1|v2|v3/...`) accepts the normal browser session cookie, so no OAuth app registration is required. Ultra's undocumented internal API (`/learn/api/v1/...`) adds read status, unread counts and to-do items.
* **Auth uses SAML2 SSO.** The chain is Ultra → `idp.kuleuven.be` (Shibboleth IdP, KU Leuven login + KU Leuven Authenticator MFA) → Ultra session cookies (httpOnly). For a native app, a **WebView login that captures the cookies afterwards** is the only practical route. The app never sees the password.
* **File downloads redirect to presigned S3 URLs.** The API returns a 302 to a presigned AWS S3 URL that is valid for about 6 h, needs no cookies and supports HTTP Range. That makes resumable downloads straightforward.
* **The only HTML we deal with is content.** Announcement bodies and Ultra "document" pages are HTML (Blackboard's BBML editor output). We render that HTML; we don't scrape it.

---

## 2. Platform and API surface

### 2.1 Hosts involved

| Host | What | Notes |
|---|---|---|
| `toledo.kuleuven.be/portal` | KU Leuven portal (Vue + Spring Boot) | Shibboleth SP; own session |
| `ultra.edu.kuleuven.cloud` | Blackboard Learn Ultra (SaaS) | SAML SP; session cookies (`BbRouter` etc.) |
| `idp.kuleuven.be` | Shibboleth IdP (KU Leuven login, MFA) | Shared SSO session for both SPs |
| `prod01-euc1-prod01-xythos.prod.files.blackboard.com` | File storage (S3, eu-central-1) | Presigned URLs, no auth |
| `icts-p-toledo-journey.cloud.icts.kuleuven.be` | Enrollment/redirect service | Maps `batchUid` to Ultra course |
| Kaltura, Collaborate Ultra, MS365 | LTI tools / video / mail counter | Out of scope for MVP |

### 2.2 Blackboard public REST API (documented, cookie session works)

Docs: <https://developer.anthology.com/portal/displayApi>. All of these were verified to return `200` with the session cookie:

| Purpose | Endpoint |
|---|---|
| Current user | `GET /learn/api/public/v1/users/me` |
| My course memberships | `GET /learn/api/public/v1/users/me/courses?expand=course&fields=...&limit=200` |
| Course details | `GET /learn/api/public/v3/courses/{courseId}` |
| Announcements | `GET /learn/api/public/v1/courses/{courseId}/announcements` |
| Content root | `GET /learn/api/public/v1/courses/{courseId}/contents` |
| Content children | `GET /learn/api/public/v1/courses/{courseId}/contents/{contentId}/children` |
| Content item (incl. HTML `body`) | `GET /learn/api/public/v1/courses/{courseId}/contents/{contentId}` |
| File attachments | `GET .../contents/{contentId}/attachments` |
| File download | `GET .../contents/{contentId}/attachments/{attachmentId}/download` → **302** to presigned S3 |
| Gradebook columns / my grades | `GET /learn/api/public/v2/courses/{courseId}/gradebook/columns`, `.../gradebook/users/{userId}` |
| Calendar | `GET /learn/api/public/v1/calendars`, `GET /learn/api/public/v1/calendars/items?since=&until=` |
| Discussions | `GET /learn/api/public/v1/courses/{courseId}/discussions` |
| Groups | `GET /learn/api/public/v2/courses/{courseId}/groups` |
| Course roster | `GET /learn/api/public/v1/courses/{courseId}/users` |
| Version | `GET /learn/api/public/v1/system/version` |

Conventions:
* IDs look like `_167831_1` (primary key). Human course codes look like `ULTRA-B-KUL-G0T42a-2627`, where `-2627` is the academic year. Communities ("organizations") use `ULTRA-C-...` and have `course.organization == true`.
* Pagination: the response has `paging.nextPage`, a relative URL you follow until it's absent. The `fields=` parameter trims payloads.
* Dates are ISO-8601 UTC.
* Some courses return **403** on `/contents` (courses that aren't available to students yet). Use `course.availability.available == "No"` to hide them or grey them out.

### 2.3 Course content model (`contentHandler.id`)

From a sample of ~70 items across 5 courses:

| Handler | Meaning | App behaviour |
|---|---|---|
| `resource/x-bb-folder` | Folder. If `isBbPage: true`, it's an Ultra page whose single child is an `ultraDocumentBody` document | Navigate / render page |
| `resource/x-bb-lesson` | Learning module | Navigate like a folder |
| `resource/x-bb-document` | Ultra document: HTML `body` with inline files | Render HTML, list embedded files |
| `resource/x-bb-file` | Single file; `contentHandler.file.{fileName,mimeType}`; one attachment | Download / open |
| `resource/x-bb-externallink` | URL | Open in Custom Tab |
| `resource/x-bb-blti-link`, `resource/x-bb-bltiplacement-*` | LTI tool (e.g. Kaltura "Lesopnames") | Open in authenticated WebView (later phase) |
| (others expected: assignment, test, discussion, survey, …) | | "Open in browser" fallback for MVP |

**Files embedded in a document body.** The HTML contains `<a data-bbfile='{"linkName","displayName","mimeType","render","resourceUrl"/"viewerUrl"}' href="https://ultra.../bbcswebdav/pid-...-dt-content-rid-..._1/xid-..._1?<signed params>">`. These `bbcswebdav` links carry short-lived signed query parameters plus the session cookie. The app should parse `data-bbfile` to show proper file chips and download through the session.

### 2.4 Ultra internal API (`/learn/api/v1`, undocumented, used by the web UI)

Useful extras, all `GET`, all verified:

| Purpose | Endpoint |
|---|---|
| Announcement read status (`readStatus.isRead`) | `GET /learn/api/v1/courses/{id}/announcements` |
| Unread announcement count | `GET /learn/api/v1/courses/{id}/announcements/counts` → `{unreadCount, postedCount}` |
| Unread grades count | `GET /learn/api/v1/courses/{id}/gradebook/unreadGradesCount` |
| To-do / due items | `GET /learn/api/v1/calendars/todo/studentItems?since=&until=` |
| Memberships incl. course metadata | `GET /learn/api/v1/users/me/memberships?expand=course` |
| Session idle time left | `GET /learn/api/v1/utilities/timeUntilBbSessionInactive?forceLogout=false` |
| Activity stream | `POST /learn/api/v1/streams/ultra` (POST, needs XSRF header, later phase) |

These may change without notice, so the app should treat them as optional enhancements with fallbacks.

### 2.5 Toledo Portal API (`https://toledo.kuleuven.be/portal/api/...`)

Separate Shibboleth session; SSO makes it a silent second login. All `GET`:

| Endpoint | Returns |
|---|---|
| `/api/enrollments` | All enrollments: title, `learningUnitId` (= Ultra `courseId` string), `favorite`, `semester`, `academicYear`, `type` (course/community), `color` |
| `/api/events` | Unified updates feed: `{type: notifications|advalvas, eventType: update|message, subType: content|course|announcement, title, source, sourceId, date, read, seen, url}` |
| `/api/upcoming` | Next lessons from the timetable, with room (`mnemonic`, `roomNumber`, map URL) |
| `/api/emails/unread` | Unread MS365 mail count |
| `/api/links` (needs header `Toledo-Language: en`), `/api/activities`, `/api/info?language=en` | Portal config / links |

This API is useful for **favourites, the semester grouping and the cross-course "updates" feed** (Phase 3 notifications). It's not required for the MVP.

### 2.6 KU Loket timetable feed (added 2026-10-19)

Lessons aren't in Blackboard's calendar. KU Loket (`www.kuleuven.be/kuloket` → SAP Fiori launchpad at `webwsp.aps.kuleuven.be`) has *Agenda → Agenda Sync iCal* (`#zr_1014-display`), which shows a personal subscription link. The page reads it with one `GET`:

| Endpoint | Returns |
|---|---|
| `https://webwsp.aps.kuleuven.be/sap/opu/odata/sap/ZC_EP_ICAL_SRV/StudentSet?sap-client=200&$format=json` | `{"d":{"results":[{"Studentid","Scheduleid","Schedulelink"}]}}` |

* KU Loket is its own SAML SP on `idp.kuleuven.be`. Without a session every URL (also the OData one) answers with an auto-submitting SAML form, so a WebView with the IdP SSO cookie ends up on the JSON by itself.
* `Schedulelink` is a TimeEdit URL (`cloud.timeedit.net/be_kuleuven/web/public/s.ics?...`): a public iCalendar feed, no cookies needed, CORS open, covering the whole academic year. Treat it as a secret (it identifies the student). It stops working when the student removes the subscription in KU Loket; a new one gives a new link.
* Feed format: UTC `DTSTART`/`DTEND`, lines folded at arbitrary points, `SUMMARY` = `<course code> <course title>: <kind>, <teacher>, <groups…>` with escaped commas, `LOCATION` = room, `UID` stable per lesson.

---

## 3. Authentication

### 3.1 Observed flow

```
App/WebView → https://ultra.edu.kuleuven.cloud/auth-saml/saml2/authenticate/_16_1?redirectUrl=<ultra url>
   ↳ auto-submitting form: POST SAMLRequest → https://idp.kuleuven.be/idp/profile/SAML2/POST/SSO
       ↳ KU Leuven login (r/q/u-number + password, then KU Leuven Authenticator approval;
         passwordless option exists). Skipped if an IdP SSO session already exists.
   ↳ POST SAMLResponse → Ultra ACS → Set-Cookie (httpOnly session cookies, e.g. BbRouter, JSESSIONID, LB cookies)
   ↳ 302 → redirectUrl (/ultra/...)
```

* Ultra session idle timeout: `timeBeforeTimeout` ≈ **3 h** observed. The absolute lifetime is unknown; we'll measure it in Phase 2.
* Logout for the portal is `Shibboleth.sso/Logout`; Ultra has its own sign-out.
* Write calls (Phase 3+) need an `X-Blackboard-XSRF` header derived from the session. The MVP is read-only, so it doesn't need this.

### 3.2 Why not OAuth

Blackboard REST supports OAuth2 three-legged auth, but only for an application registered with Anthology **and enabled by KU Leuven's Blackboard admins**. That's not realistic for a personal client. The KU Leuven IdP also won't issue tokens to an unregistered app. So **the session cookie is the credential.**

### 3.3 Proposed native login

1. Show a full-screen **Android `WebView`** at the `auth-saml` URL above, with `redirectUrl=https://ultra.edu.kuleuven.cloud/ultra/course`. The user types credentials into KU Leuven's own page, so **the app never handles the password**. We inject no JavaScript into IdP pages, and we turn off WebView form-data saving.
2. In `WebViewClient`, detect navigation back to `https://ultra.edu.kuleuven.cloud/ultra/...`.
3. Read cookies with `CookieManager.getCookie("https://ultra.edu.kuleuven.cloud")`, which includes httpOnly cookies. Hand them to OkHttp via a custom `CookieJar`.
4. Validate with `GET /learn/api/public/v1/users/me`, then store the user id and display name.
5. **Secure storage:** serialize the cookies and encrypt them with **Tink AEAD using an Android Keystore-backed master key**, stored in DataStore. `EncryptedSharedPreferences` is deprecated, so we avoid it. Exclude the store from backup (`dataExtractionRules` / `fullBackupContent`). Nothing is logged; the OkHttp logging interceptor redacts `Cookie`/`Set-Cookie`.
6. **Session expiry:** if the API returns 401, an OkHttp `Authenticator`/interceptor triggers a **silent re-login**. That's a hidden WebView reloading the `auth-saml` URL, which succeeds without UI while the IdP SSO cookie (kept in the WebView's own cookie store) is still valid. If that fails, it falls back to a "Session expired, sign in again" prompt. Cached data stays usable offline in the meantime.
7. **MFA on the same phone:** the IdP page may offer to open the KU Leuven Authenticator app (custom scheme / `intent://` link). The WebView must hand non-`http(s)` URLs to `startActivity`, and the login has to survive being backgrounded while the user approves. **Verify on a real device in Phase 2.**
8. **Logout:** clear the encrypted store, WebView cookies (`CookieManager.removeAllCookies`), the Room cache and downloaded files (with an option to keep downloads).

(Optional later: load `https://toledo.kuleuven.be/portal/` in the same hidden WebView after login to obtain the portal session silently.)

---

## 4. Toledo feature catalogue

### Global (Ultra base navigation + portal)
| Feature | Source | Phase |
|---|---|---|
| Course list (courses + communities/"Organizations"), favourites, semester | Bb `users/me/courses`, portal `enrollments` | **MVP** |
| Profile (name, avatar, language, notification settings) | Bb `users/me` | MVP (read-only account screen) |
| Activity stream (new content, announcements, due items, grades) | Bb stream (POST) / portal `events` | Later: updates/notifications |
| Calendar (course events, deadlines, institution calendar) | Bb `calendars/items` | Later |
| To-do (overdue / due today / due soon) | Bb internal `todo/studentItems` | Later |
| Messages (course messages / conversations) | Bb internal | Later |
| Grades (overall + per course, unread grades) | Bb gradebook v2 | Later |
| Tools: portfolios, "My files", Kaltura My Media | Ultra / LTI | Later / webview |
| Upcoming lessons with rooms (timetable from KU Loket) | Portal `upcoming` | Later (nice for a "Today" widget) |
| Unread KU Leuven mail count | Portal `emails/unread` | Later / optional |
| Search over Toledo | Portal `toledo-search` | Later |
| Links to KU Loket (results, ISP, timetable, study progress), library, Office 365 | Portal `links` | Later (simple link list) |
| Ad Valvas messages (faculty announcements) | Portal `events` (`advalvas`) | Later |

### Inside a course (Ultra tabs: Content, Calendar, Announcements, Discussions, Gradebook, Groups)
| Feature | Phase |
|---|---|
| **Content outline**: folders, learning modules, documents, files, links | **MVP** |
| **File viewing & download** (attachments + files embedded in documents) | **MVP** |
| **Announcements** (HTML, date, author, unread state) | **MVP** |
| Course leaders (instructors, contact) | MVP-lite (course header) or later |
| Progress tracking ("started / not started" per item) | Later |
| Assignments & tests (view, due dates, submission status). **Submission stays out of scope unless you ask** | Later |
| Discussions | Later |
| Gradebook / feedback | Later |
| Groups | Later |
| Course calendar / schedule | Later |
| Lecture recordings (Kaltura LTI), Collaborate sessions | Later (authenticated WebView) |
| Attendance, achievements, course search | Later |

---

## 5. MVP proposal

### Scope (read-only)
1. **Login**: welcome screen, then the WebView SSO, then home. Silent re-auth, logout.
2. **Courses**: current academic year first, grouped as *Courses* / *Communities*, with search/filter and "show older/unavailable". Each card shows an unread-announcement badge and is coloured by course (portal colour or a stable hash).
3. **Course detail** with two tabs, **Content** and **Announcements**.
4. **Content browser**: hierarchical navigation with breadcrumbs. Documents render as readable HTML with embedded file chips. Files open in an external viewer via `FileProvider` + `ACTION_VIEW`. Links open in Custom Tabs. Unsupported types open in the browser.
5. **Announcements**: list (newest first, unread dot) and detail (HTML). An aggregated cross-course feed could be a nice bonus (see questions).
6. **Downloads**: WorkManager + OkHttp with progress, retry and Range resume, stored in app-specific storage. "Save to Downloads" exports via MediaStore. Offline badge on downloaded files, plus a Downloads screen.
7. **Offline**: Room is the single source of truth for courses, content tree and announcements. Pull-to-refresh, last-synced time, and an offline banner.
8. **Settings/Account**: theme (system/light/dark), dynamic colour toggle, storage used, clear cache, logout.

### Adaptive UX
* `NavigationSuiteScaffold` gives a bottom bar on compact width, a navigation rail on medium and a drawer/rail on expanded. Top-level destinations: **Courses**, **Announcements** (all courses), **Downloads**, **Settings**.
* `ListDetailPaneScaffold`:
  * Compact: Courses → Course → Folder → Item as a single-pane stack with predictive back.
  * Medium/expanded: course list | course detail side by side. On expanded width, opening a document or announcement shows it in the extra pane (`SupportingPaneScaffold` or the list-detail extra pane), so you can browse a folder and read a document at the same time.
* Everything is driven by `currentWindowAdaptiveInfo()`, so freeform/resizable windows, split screen, foldables and tablets reflow live. State survives config changes through ViewModels and `rememberSaveable`. Manifest: `resizeableActivity=true`, no orientation lock.
* Material 3 Expressive-ready theme, dynamic colour on API 31+ with a KU Leuven-blue fallback scheme, edge-to-edge, dark mode.

### Architecture

```
:app                    – Application, MainActivity, NavHost, adaptive scaffold, Hilt entry
:core:model             – pure Kotlin domain models (Course, ContentNode, Announcement, Attachment…)
:core:network           – Retrofit + OkHttp + kotlinx.serialization DTOs, CookieJar, auth interceptor
:core:auth              – WebView login, session store (Tink + DataStore), re-auth coordinator
:core:database          – Room (entities, DAOs, migrations)
:core:data              – repositories (offline-first), mappers, sync, download manager (WorkManager)
:core:designsystem      – theme, typography, components (HTML view, file chip, empty/error states)
:feature:courses        – course list + course detail shell
:feature:content        – content browser, document viewer
:feature:announcements  – per-course + aggregated announcements
:feature:downloads      – downloads screen
:feature:settings       – settings/account
```

* **MVVM + UDF**: each screen has a `ViewModel` exposing `StateFlow<UiState>` and taking events. Repositories expose `Flow` from Room and refresh from the network (stale-while-revalidate).
* **DI**: Hilt (with KSP), `hilt-navigation-compose`, `hilt-work`.
* **Networking**: **Retrofit + OkHttp + kotlinx.serialization**. I prefer this over Ktor because OkHttp gives us CookieJar, `Authenticator` and interceptors for the re-auth flow, plus MockWebServer for tests. Unknown JSON fields are ignored (`ignoreUnknownKeys`), so new Blackboard fields don't break parsing.
* **Persistence**: Room (KSP) for the cache. Proto/Preferences DataStore for settings. Tink-encrypted DataStore for cookies.
* **Navigation**: Navigation Compose with type-safe routes, plus `material3-adaptive-navigation` for panes.
* **Images**: Coil 3 sharing the OkHttp client (course banners and avatars need the cookie).
* **HTML rendering**: a small themed `WebView` component for announcement and document bodies. JS is off, CSS follows the M3 colour scheme in light and dark, link taps are intercepted, and `bbcswebdav` / `data-bbfile` links route to our download flow. This handles tables, images and lists far better than `AnnotatedString.fromHtml`.
* **Build**: Gradle Kotlin DSL, version catalog (`libs.versions.toml`), Kotlin 2.x + Compose compiler plugin, AGP 8.x/9.x, compileSdk/targetSdk 36, **minSdk 26**, R8 for release.
* **Quality**: unit tests for mappers, repositories and the auth coordinator using MockWebServer with **anonymised** JSON fixtures shaped like the responses above. A couple of Compose UI tests. ktlint/detekt optional.

### Risks
* **Internal `/learn/api/v1` endpoints** can change. Mitigation: use them only for badges/read state, with graceful fallback.
* **IdP/MFA quirks inside WebView** (e.g. authenticator app handoff, or Microsoft/third-party pages). Mitigation: handle `intent://` and custom schemes. As a last resort, Custom Tabs can't share cookies with the app, so WebView remains the plan.
* **Acceptable use**: this is an unofficial client acting as you, at human-like request rates. No crawling, conservative sync, and requests are identified with a normal user agent.
* **Session lifetime**: if the IdP SSO cookie is short-lived, you'll re-approve MFA now and then. We'll measure this.

---

## 6. Open questions (defaults in brackets)

1. App name / `applicationId`. "Toledo" is KU Leuven's brand, so an unofficial name may be wiser. [`be.kuleuven.toledo.unofficial` → suggest e.g. "Tolly" / `be.tolly`]
2. UI language: English, Dutch, or both via string resources? [English + Dutch]
3. Top-level MVP destinations: Courses only, or also an aggregated Announcements feed and a Downloads screen? [all four as listed above]
4. Mark announcements as read on the server when opened? That's a write call. [No, local read state only]
5. Download location: app-private with "Save to Downloads", or straight to `Downloads/Toledo/<course>/`? [app-private + export]
6. Show communities/organizations (library, faculty info, …) in the MVP? [Yes, in a separate collapsed section]
7. Module layout: multi-module as above, or a single `:app` module with clean packages for speed? [multi-module, light]
8. Test device: which phone / Android version, and do you have a tablet or foldable? [physical phone + resizable emulator]
9. Should I `git init` the repo, and do you want a licence? [git init, no licence yet]

### Decisions (answered 2026-10-18)
* **Name:** unofficial name, working title **"Tolly"** (`applicationId` `be.tolly`), can be renamed before release.
* **MVP destinations:** Courses, Announcements feed (all courses), Downloads, Settings.
* **Language:** English + Dutch string resources, following the system language.
* **Downloads:** app-private storage for offline use, plus a "Save to Downloads" export action.
* Remaining questions (4, 6–9) use the bracketed defaults unless changed.
