// Every string on the site, in English and Dutch (the app's two languages). Pages get the right
// half with `strings(lang)`; keep the two in step.

export const languages = { en: "English", nl: "Nederlands" } as const;
export type Lang = keyof typeof languages;

export const links = {
  repo: "https://github.com/Bueezi/tolly",
  apk: "https://github.com/Bueezi/tolly/releases/latest",
  issues: "https://github.com/Bueezi/tolly/issues",
  license: "https://github.com/Bueezi/tolly/blob/main/LICENSE",
  email: "hello@tolly.be",
  // The closed test (tolly.be/alpha): joining the group makes a Google account a tester.
  alphaGroup: "https://groups.google.com/g/tolly-alpha",
  alphaOptIn: "https://play.google.com/apps/testing/be.tolly",
  play: "https://play.google.com/store/apps/details?id=be.tolly",
};

const en = {
  meta: {
    title: "Tolly: KU Leuven's Toledo on Android",
    description:
      "A modern Android client for KU Leuven's Toledo: courses, announcements, agenda, grades and files, also offline. Free and open source.",
  },
  skip: "Skip to content",
  nav: { features: "Features", looks: "Two looks", privacy: "Privacy", download: "Download" },
  theme: { toLight: "Switch to light mode", toDark: "Switch to dark mode" },
  langSwitch: { label: "Nederlands", short: "NL" },
  hero: {
    title: "Toledo, the way it should be.",
    lead:
      "Tolly is an unofficial, modern Android client for KU Leuven's Toledo. Your courses, announcements, agenda, grades and files, also when you're offline.",
    meta: ["Free and open source", "No ads or tracking"],
    shotFront: "Tolly's course list, showing which course has class now",
    shotBack: "The same course list in Tolly's other look",
  },
  cta: {
    apk: "Download the APK",
    play: "Join the alpha test",
  },
  features: {
    eyebrow: "Features",
    title: "What you use in Toledo, and some extras.",
    lead:
      "Tolly keeps what you open on your phone, so it loads right away and still works without a connection.",
    courses: {
      title: "Know where your next class is",
      body: "Each course gets an icon and a colour. The list shows which class is on now, until when and in which room.",
      alt: "The course list, with a class going on now in Wiskunde I",
    },
    content: {
      title: "All the course content",
      body: "Folders, learning modules, pages, files and links, as in Toledo. Tests and assignments open in the app without signing in again.",
      alt: "A course's content: a learning module with lecture slides, a folder and the syllabus",
    },
    agenda: {
      title: "Lessons and deadlines together",
      body: "Your KU Leuven timetable and Toledo's deadlines in one agenda, as a list, a day or a week.",
      alt: "The agenda with today's lessons and tomorrow's deadline",
    },
    news: {
      title: "Announcements",
      body: "Per course or all together, with an unread filter, swipe to mark as read, and notifications if you want them.",
      alt: "Announcements from all courses, unread ones in bold",
    },
    small: [
      { icon: "grade", title: "Grades", body: "Per course or all at once, with feedback." },
      { icon: "play", title: "Recordings", body: "Course recordings in a built-in player." },
      { icon: "download", title: "Downloads", body: "In the background or a whole course at once, copied to Downloads/Tolly if you like." },
      { icon: "offline", title: "Offline", body: "Everything you've opened stays available without a connection." },
    ],
  },
  looks: {
    eyebrow: "Two looks",
    title: "Material or Liquid Glass",
    lead: "Switch in the settings, or shake your phone. Both come in light and dark.",
    lookLabel: "Look",
    schemeLabel: "Theme",
    light: "Light",
    dark: "Dark",
    material: {
      name: "Material 3 Expressive",
      body: "Rounded, grouped lists, tonal colours and Android's dynamic colour from your wallpaper.",
    },
    glass: {
      name: "Liquid Glass",
      body: "After iOS 26: a floating glass tab bar with a lens you can drag, and glass buttons over the content.",
    },
    alt: "Tolly's course list in the {look} look, {scheme} theme",
  },
  tablet: {
    eyebrow: "Tablets and foldables",
    title: "Room for two panes.",
    body: "On a tablet or an unfolded phone, the list and its details sit side by side. Drag the divider to resize them, or fold a pane away.",
    alt: "Tolly on a tablet: the course list next to a course's content",
  },
  privacy: {
    eyebrow: "Privacy",
    title: "Tolly never sees your password.",
    lead: "Your data goes between your phone and KU Leuven, and nowhere else.",
    points: [
      { icon: "lock", title: "KU Leuven's own sign-in", body: "You sign in on KU Leuven's login page, two-factor authentication included." },
      { icon: "block", title: "No servers of our own", body: "No accounts, ads, analytics or tracking." },
      { icon: "code", title: "Open source", body: "The code is public under the AGPL-3.0, so anyone can check what the app does." },
    ],
    flow: { phone: "Your phone", kul: "KU Leuven", direct: "Direct connection" },
    more: "Read the privacy policy",
  },
  download: {
    title: "Get Tolly",
    body: "Install the APK from GitHub, or join the alpha test on Google Play.",
  },
  alpha: {
    meta: {
      title: "Test Tolly on Google Play",
      description: "Join Tolly's closed test on Google Play in three steps.",
    },
    eyebrow: "Closed test",
    title: "Test Tolly on Google Play",
    lead:
      "Tolly is in a closed test on Google Play before it goes public. Join in three steps, with the Google account that's on your phone.",
    steps: [
      {
        title: "Join the testers group",
        body: "Open the group and tap Join group. Being in it is what lets your account into the test.",
        button: "Join the group",
      },
      {
        title: "Become a tester",
        body: "Open the test's page on Google Play and tap Become a tester.",
        button: "Join on the web",
      },
      {
        title: "Install Tolly",
        body: "Install it from Google Play like any other app. Right after joining, it can take a few minutes before Play shows it.",
        button: "Open in Google Play",
      },
    ],
    stay: {
      title: "Please stay for two weeks",
      body: "Google only lets Tolly go public after at least 12 testers have stayed in the test for 14 days in a row, so please don't leave the group or the test before then.",
    },
    feedback: "Found a bug or have an idea? Mail me at",
    or: "or",
    issues: "open an issue on GitHub",
  },
  footer: {
    disclaimer:
      "Tolly is an unofficial, independent project. It is not made, endorsed or supported by KU Leuven. Toledo and KU Leuven are names of KU Leuven, used here only to say what Tolly works with.",
    made: "AI was involved in the development of the application.",
    contact: "Questions or ideas? Mail me at",
    github: "GitHub",
    issues: "Report a bug",
    license: "License: AGPL-3.0",
    privacy: "Privacy policy",
  },
};

const nl: typeof en = {
  meta: {
    title: "Tolly: Toledo van KU Leuven op Android",
    description:
      "Een moderne Android-client voor Toledo van KU Leuven: vakken, aankondigingen, agenda, punten en bestanden, ook offline. Gratis en open source.",
  },
  skip: "Naar de inhoud",
  nav: { features: "Functies", looks: "Twee stijlen", privacy: "Privacy", download: "Download" },
  theme: { toLight: "Naar lichte modus", toDark: "Naar donkere modus" },
  langSwitch: { label: "English", short: "EN" },
  hero: {
    title: "Toledo, zoals het hoort.",
    lead:
      "Tolly is een onofficiële, moderne Android-client voor Toledo van KU Leuven. Je vakken, aankondigingen, agenda, punten en bestanden, ook als je offline bent.",
    meta: ["Gratis en open source", "Geen advertenties of tracking"],
    shotFront: "De vakkenlijst van Tolly, met het vak waarin je nu les hebt",
    shotBack: "Dezelfde vakkenlijst in de andere stijl van Tolly",
  },
  cta: {
    apk: "Download de APK",
    play: "Doe mee aan de alfatest",
  },
  features: {
    eyebrow: "Functies",
    title: "Wat je in Toledo gebruikt, en meer.",
    lead:
      "Tolly bewaart wat je opent op je gsm, zodat het meteen laadt en ook zonder verbinding werkt.",
    courses: {
      title: "Weet waar je volgende les is",
      body: "Elk vak krijgt een icoon en een kleur. De lijst toont welke les nu bezig is, tot wanneer en in welk lokaal.",
      alt: "De vakkenlijst, met een les die nu bezig is in Wiskunde I",
    },
    content: {
      title: "Alle cursusinhoud",
      body: "Mappen, leermodules, pagina's, bestanden en links, zoals in Toledo. Toetsen en opdrachten openen in de app zonder opnieuw aan te melden.",
      alt: "De inhoud van een vak: een leermodule met slides, een map en de syllabus",
    },
    agenda: {
      title: "Lessen en deadlines samen",
      body: "Je uurrooster van KU Leuven en de deadlines uit Toledo in één agenda, als lijst, dag of week.",
      alt: "De agenda met de lessen van vandaag en de deadline van morgen",
    },
    news: {
      title: "Aankondigingen",
      body: "Per vak of allemaal samen, met een filter voor ongelezen, vegen om te markeren en meldingen als je wilt.",
      alt: "Aankondigingen van alle vakken, ongelezen in het vet",
    },
    small: [
      { icon: "grade", title: "Punten", body: "Per vak of allemaal samen, met feedback." },
      { icon: "play", title: "Opnames", body: "Lesopnames in een ingebouwde speler." },
      { icon: "download", title: "Downloads", body: "Op de achtergrond of een heel vak in één keer, als je wilt ook in Downloads/Tolly." },
      { icon: "offline", title: "Offline", body: "Alles wat je opende, blijft beschikbaar zonder verbinding." },
    ],
  },
  looks: {
    eyebrow: "Twee stijlen",
    title: "Material of Liquid Glass",
    lead: "Wissel in de instellingen, of schud je gsm. Allebei in licht en donker.",
    lookLabel: "Stijl",
    schemeLabel: "Thema",
    light: "Licht",
    dark: "Donker",
    material: {
      name: "Material 3 Expressive",
      body: "Afgeronde, gegroepeerde lijsten, tonale kleuren en de dynamische kleuren van Android uit je achtergrond.",
    },
    glass: {
      name: "Liquid Glass",
      body: "Naar iOS 26: een zwevende glazen tabbalk met een lens die je kunt verslepen, en glazen knoppen boven de inhoud.",
    },
    alt: "De vakkenlijst van Tolly in de stijl {look}, thema {scheme}",
  },
  tablet: {
    eyebrow: "Tablets en plooitoestellen",
    title: "Plaats voor twee panelen.",
    body: "Op een tablet of een opengeplooide gsm staan de lijst en de details naast elkaar. Sleep de scheiding om ze groter of kleiner te maken, of plooi een paneel weg.",
    alt: "Tolly op een tablet: de vakkenlijst naast de inhoud van een vak",
  },
  privacy: {
    eyebrow: "Privacy",
    title: "Tolly ziet je wachtwoord nooit.",
    lead: "Je gegevens gaan tussen je gsm en KU Leuven, en nergens anders heen.",
    points: [
      { icon: "lock", title: "Aanmelden bij KU Leuven zelf", body: "Je meldt aan op de aanmeldpagina van KU Leuven, met tweestapsverificatie." },
      { icon: "block", title: "Geen eigen servers", body: "Geen accounts, advertenties, analytics of tracking." },
      { icon: "code", title: "Open source", body: "De code is openbaar onder de AGPL-3.0, zodat iedereen kan nagaan wat de app doet." },
    ],
    flow: { phone: "Je gsm", kul: "KU Leuven", direct: "Rechtstreekse verbinding" },
    more: "Lees het privacybeleid",
  },
  download: {
    title: "Download Tolly",
    body: "Installeer de APK van GitHub, of doe mee aan de alfatest op Google Play.",
  },
  alpha: {
    meta: {
      title: "Test Tolly op Google Play",
      description: "Doe in drie stappen mee aan de gesloten test van Tolly op Google Play.",
    },
    eyebrow: "Gesloten test",
    title: "Test Tolly op Google Play",
    lead:
      "Tolly is in een gesloten test op Google Play voor het openbaar wordt. Doe mee in drie stappen, met het Google-account dat op je gsm staat.",
    steps: [
      {
        title: "Word lid van de testgroep",
        body: "Open de groep en word lid. Als lid kan je account meedoen aan de test.",
        button: "Naar de groep",
      },
      {
        title: "Word tester",
        body: "Open de pagina van de test op Google Play en tik op de knop om tester te worden.",
        button: "Meedoen via het web",
      },
      {
        title: "Installeer Tolly",
        body: "Installeer de app via Google Play zoals elke andere app. Vlak na het meedoen kan het een paar minuten duren voor Play hem toont.",
        button: "Openen in Google Play",
      },
    ],
    stay: {
      title: "Blijf twee weken mee",
      body: "Google laat Tolly pas openbaar gaan als minstens 12 testers 14 dagen na elkaar in de test zitten. Verlaat de groep of de test dus niet voor die tijd.",
    },
    feedback: "Een bug gevonden of een idee? Mail me op",
    or: "of",
    issues: "meld het op GitHub",
  },
  footer: {
    disclaimer:
      "Tolly is een onofficieel, onafhankelijk project. Het is niet gemaakt, goedgekeurd of ondersteund door KU Leuven. Toledo en KU Leuven zijn namen van KU Leuven, hier enkel gebruikt om te zeggen waarmee Tolly werkt.",
    made: "Bij de ontwikkeling van de app werd AI gebruikt.",
    contact: "Vragen of ideeën? Mail me op",
    github: "GitHub",
    issues: "Meld een bug",
    license: "Licentie: AGPL-3.0",
    privacy: "Privacybeleid",
  },
};

const dictionaries = { en, nl };

export function strings(lang: Lang) {
  return dictionaries[lang];
}

/** The URL of a page (`""` for home, `"privacy"`) in a language: English has no prefix. */
export function pathFor(lang: Lang, page = "") {
  const base = lang === "en" ? "" : `/${lang}`;
  return page ? `${base}/${page}` : base || "/";
}

export const otherLang = (lang: Lang): Lang => (lang === "en" ? "nl" : "en");
