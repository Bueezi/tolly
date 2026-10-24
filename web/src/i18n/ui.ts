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
      "A fast Android app for KU Leuven's Toledo: courses, announcements, agenda, grades and files in one place. Free, open source and private.",
  },
  skip: "Skip to content",
  nav: { features: "Features", looks: "Two looks", privacy: "Privacy", download: "Download" },
  theme: { toLight: "Switch to light mode", toDark: "Switch to dark mode" },
  langSwitch: { label: "Nederlands", short: "NL" },
  hero: {
    title: "Toledo, the way it should be.",
    lead:
      "Tolly is a fast Android app for KU Leuven students: your courses, announcements, agenda, grades and files in one place.",
    meta: ["Free and open source", "No ads or tracking"],
    shotFront: "Tolly's course list, showing which course has class now",
    shotBack: "The same course list in Tolly's other look",
  },
  cta: {
    apk: "Download the APK",
    play: "Google Play: coming soon",
  },
  features: {
    eyebrow: "Features",
    title: "Everything from Toledo, and a few things it can't do.",
    lead:
      "Tolly shows the same courses and files you see on Toledo, and keeps them on your phone so they open at once, with or without a connection.",
    courses: {
      title: "Know where your next class is",
      body: "Each course gets an icon and colour. Tolly shows which one you have class in right now, until when, and in which room.",
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
      title: "Announcements you won't miss",
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
    title: "Material or Liquid Glass. Your pick.",
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
      { icon: "block", title: "No servers of our own", body: "No accounts, ads, analytics or tracking. There's nothing to send your data to." },
      { icon: "code", title: "Open source", body: "The code is public under the AGPL-3.0, so anyone can check what the app does." },
    ],
    flow: { phone: "Your phone", kul: "KU Leuven", direct: "Direct, nothing in between" },
    more: "Read the privacy policy",
  },
  download: {
    title: "Get Tolly",
    body: "Install the APK from GitHub. Google Play is on its way.",
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
    made: "Built with Claude by a student.",
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
      "Een snelle Android-app voor Toledo van KU Leuven: vakken, aankondigingen, agenda, punten en bestanden op één plek. Gratis, open source en privé.",
  },
  skip: "Naar de inhoud",
  nav: { features: "Functies", looks: "Twee stijlen", privacy: "Privacy", download: "Download" },
  theme: { toLight: "Naar lichte modus", toDark: "Naar donkere modus" },
  langSwitch: { label: "English", short: "EN" },
  hero: {
    title: "Toledo, zoals het hoort.",
    lead:
      "Tolly is een snelle Android-app voor studenten van KU Leuven: je vakken, aankondigingen, agenda, punten en bestanden op één plek.",
    meta: ["Gratis en open source", "Geen advertenties of tracking"],
    shotFront: "De vakkenlijst van Tolly, met het vak waarin je nu les hebt",
    shotBack: "Dezelfde vakkenlijst in de andere stijl van Tolly",
  },
  cta: {
    apk: "Download de APK",
    play: "Google Play: binnenkort",
  },
  features: {
    eyebrow: "Functies",
    title: "Alles uit Toledo, en wat Toledo zelf niet kan.",
    lead:
      "Tolly toont dezelfde vakken en bestanden als Toledo, en bewaart ze op je gsm zodat ze meteen openen, met of zonder verbinding.",
    courses: {
      title: "Weet waar je volgende les is",
      body: "Elk vak krijgt een icoon en kleur. Tolly toont in welk vak je nu les hebt, tot wanneer en in welk lokaal.",
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
      title: "Aankondigingen die je niet mist",
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
    title: "Material of Liquid Glass. Jij kiest.",
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
      { icon: "block", title: "Geen eigen servers", body: "Geen accounts, advertenties, analytics of tracking. Er is niets om je gegevens naartoe te sturen." },
      { icon: "code", title: "Open source", body: "De code is openbaar onder de AGPL-3.0, zodat iedereen kan nagaan wat de app doet." },
    ],
    flow: { phone: "Je gsm", kul: "KU Leuven", direct: "Rechtstreeks, niets ertussen" },
    more: "Lees het privacybeleid",
  },
  download: {
    title: "Download Tolly",
    body: "Installeer de APK van GitHub. Google Play komt eraan.",
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
    feedback: "Een fout gevonden of een idee? Mail me op",
    or: "of",
    issues: "meld het op GitHub",
  },
  footer: {
    disclaimer:
      "Tolly is een onofficieel, onafhankelijk project. Het is niet gemaakt, goedgekeurd of ondersteund door KU Leuven. Toledo en KU Leuven zijn namen van KU Leuven, hier enkel gebruikt om te zeggen waarmee Tolly werkt.",
    made: "Gebouwd met Claude door een student.",
    contact: "Vragen of ideeën? Mail me op",
    github: "GitHub",
    issues: "Meld een fout",
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
