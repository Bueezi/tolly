// Renders the Google Play graphics and the website's share image from the demo screenshots in
// screens/<lang>/: each one is an HTML page, screenshotted by headless Chromium at its exact size.
//
//   node brand/store/render.mjs          (needs `chromium` on PATH and web/'s npm install, for the font)
//
// Writes android/fastlane/metadata/android/<locale>/images/{phoneScreenshots,tenInchScreenshots,
// featureGraphic.png}, web/public/{og.png,apple-touch-icon.png} and the README's
// brand/poster.png. Retake the screenshots with
// capture.mjs when the UI changes.

import { readFileSync, rmSync } from "node:fs";
import { join } from "node:path";
import { pathToFileURL } from "node:url";
import { font, renderHtml as render, repo, screensDir } from "./chromium.mjs";

const icon = readFileSync(join(repo, "brand/icon.svg"), "utf8");
const screen = (lang, name) => pathToFileURL(join(screensDir, lang, `${name}.png`)).href;
const fastlane = (locale) => join(repo, "android/fastlane/metadata/android", locale, "images");

const base = (width, height, body, extraCss = "") => `<!doctype html>
<html><head><meta charset="utf-8"><style>
@font-face { font-family: "Jakarta"; src: url("${font}") format("woff2"); font-weight: 200 800; }
* { box-sizing: border-box; margin: 0; }
html, body { width: ${width}px; height: ${height}px; overflow: hidden; }
body { font-family: "Jakarta", sans-serif; color: #fff; -webkit-font-smoothing: antialiased; }
.bg { position: absolute; inset: 0; overflow: hidden; }
.bg.indigo { background: radial-gradient(70% 50% at 80% 10%, #7b7bff 0%, transparent 70%), linear-gradient(165deg, #5b5bf0 0%, #3b2fb8 60%, #2a1f8f 100%); }
.bg.night { background: radial-gradient(70% 50% at 80% 10%, rgb(91 91 240 / .55) 0%, transparent 70%), linear-gradient(165deg, #1d1a5e 0%, #0d0b2a 100%); }
.device { position: absolute; background: #0c0c12; box-shadow: 0 0 0 2px rgb(255 255 255 / .12) inset, 0 40px 90px -20px rgb(0 0 0 / .6); }
.device img { display: block; width: 100%; }
/* A phone screen without its 74 px status bar (of 1080 × 2400), as on the website. */
.screen { overflow: hidden; aspect-ratio: 1080 / 2326; }
.screen img { margin-top: -6.852%; }
h1 { font-weight: 800; letter-spacing: -0.035em; line-height: 1.05; text-wrap: balance; }
${extraCss}
</style></head><body>${body}</body></html>`;

// A phone screenshot under a headline, the phone running off the bottom edge. 1080 × 1920 (9:16).
function phoneShot(lang, name, title, theme) {
  const w = 1080, h = 1920, pw = 800;
  return base(w, h, `
    <div class="bg ${theme}"></div>
    <h1 style="position:absolute;left:90px;right:90px;top:150px;font-size:84px;text-align:center">${title}</h1>
    <div class="device" style="left:${(w - pw) / 2}px;top:500px;width:${pw}px;padding:22px;border-radius:100px">
      <div class="screen" style="border-radius:78px"><img src="${screen(lang, name)}"></div>
    </div>`);
}

// A tablet screenshot under a headline. 2560 × 1440 (16:9).
function tabletShot(lang, name, title, theme) {
  const w = 2560, h = 1440, tw = 2080;
  return base(w, h, `
    <div class="bg ${theme}"></div>
    <h1 style="position:absolute;left:160px;right:160px;top:110px;font-size:92px;text-align:center">${title}</h1>
    <div class="device" style="left:${(w - tw) / 2}px;top:330px;width:${tw}px;padding:26px;border-radius:64px">
      <img src="${screen(lang, name)}" style="border-radius:40px">
    </div>`);
}

// The logo, name and tagline beside two phones: Play's feature graphic and the site's share image.
function banner(lang, tagline, width, height) {
  const s = height / 500;
  return base(width, height, `
    <div class="bg indigo"></div>
    <div style="position:absolute;left:${70 * s}px;top:50%;transform:translateY(-50%);width:${470 * s}px">
      <div style="display:flex;align-items:center;gap:${22 * s}px;margin-bottom:${26 * s}px">
        <div style="width:${96 * s}px;height:${96 * s}px;border-radius:${24 * s}px;overflow:hidden;box-shadow:0 0 0 ${2 * s}px rgb(255 255 255 / .25),0 ${16 * s}px ${36 * s}px -${10 * s}px rgb(0 0 0 / .5)">${icon.replace(/width="512" height="512"/, 'width="100%" height="100%"')}</div>
        <div style="font-weight:800;font-size:${74 * s}px;letter-spacing:-0.04em">Tolly</div>
      </div>
      <h1 style="font-size:${46 * s}px">${tagline}</h1>
    </div>
    <div class="device" style="left:${width - 250 * s}px;top:${60 * s}px;width:${215 * s}px;padding:${7 * s}px;border-radius:${28 * s}px;transform:rotate(7deg)">
      <div class="screen" style="border-radius:${21 * s}px"><img src="${screen(lang, "g-dark-courses")}"></div>
    </div>
    <div class="device" style="left:${width - 440 * s}px;top:${100 * s}px;width:${235 * s}px;padding:${7 * s}px;border-radius:${30 * s}px;transform:rotate(-5deg)">
      <div class="screen" style="border-radius:${23 * s}px"><img src="${screen(lang, "m-light-courses")}"></div>
    </div>`);
}

// The README's poster: the logo and tagline over four phones in both looks and themes. 2400 × 1260.
function poster() {
  const w = 2400, h = 1260, pw = 440, gap = 60;
  const shots = ["m-light-courses", "m-dark-agenda", "g-light-courses", "g-dark-course"];
  const left = (w - shots.length * pw - (shots.length - 1) * gap) / 2;
  const phones = shots.map((name, i) => `
    <div class="device" style="left:${left + i * (pw + gap)}px;top:${i % 2 ? 470 : 420}px;width:${pw}px;padding:14px;border-radius:58px">
      <div class="screen" style="border-radius:46px"><img src="${screen("en", name)}"></div>
    </div>`).join("");
  return base(w, h, `
    <div class="bg indigo"></div>
    <div style="position:absolute;left:0;right:0;top:110px;display:flex;flex-direction:column;align-items:center;gap:30px">
      <div style="display:flex;align-items:center;gap:30px">
        <div style="width:130px;height:130px;border-radius:32px;overflow:hidden;box-shadow:0 0 0 3px rgb(255 255 255 / .25),0 20px 44px -12px rgb(0 0 0 / .5)">${icon.replace(/width="512" height="512"/, 'width="100%" height="100%"')}</div>
        <div style="font-weight:800;font-size:120px;letter-spacing:-0.04em">Tolly</div>
      </div>
      <h1 style="font-size:64px;font-weight:700;opacity:.92">Toledo, the way it should be.</h1>
    </div>${phones}`);
}

const phones = {
  en: [
    ["m-light-courses", "Know where your next class is", "indigo"],
    ["m-light-course", "Everything from Toledo, in one app", "indigo"],
    ["m-light-agenda", "Lessons and deadlines in one agenda", "indigo"],
    ["m-light-news", "Never miss an announcement", "indigo"],
    ["g-dark-courses", "Liquid Glass or Material, light or dark", "night"],
    ["g-dark-course", "Files open at once, even offline", "night"],
  ],
  nl: [
    ["m-light-courses", "Weet waar je volgende les is", "indigo"],
    ["m-light-course", "Alles uit Toledo, in één app", "indigo"],
    ["m-light-agenda", "Lessen en deadlines in één agenda", "indigo"],
    ["m-light-news", "Mis geen enkele aankondiging", "indigo"],
    ["g-dark-courses", "Liquid Glass of Material, licht of donker", "night"],
    ["g-dark-course", "Bestanden openen meteen, ook offline", "night"],
  ],
};
const tablets = {
  en: [
    ["t-g-light-courses", "Lists and details side by side", "indigo"],
    ["t-m-dark-agenda", "Your week and its deadlines at a glance", "night"],
    ["t-m-light-courses", "Room for every course", "indigo"],
    ["t-g-light-agenda", "Two looks, on every screen size", "indigo"],
  ],
  nl: [
    ["t-g-light-courses", "Lijst en details naast elkaar", "indigo"],
    ["t-m-dark-agenda", "Je week en je deadlines in één oogopslag", "night"],
    ["t-m-light-courses", "Ruimte voor elk vak", "indigo"],
    ["t-g-light-agenda", "Twee stijlen, op elk schermformaat", "indigo"],
  ],
};
const taglines = { en: "Toledo, the way it should be.", nl: "Toledo, zoals het hoort." };
const locales = { en: "en-US", nl: "nl-NL" };

for (const [lang, shots] of Object.entries(phones)) {
  const dir = fastlane(locales[lang]);
  rmSync(join(dir, "phoneScreenshots"), { recursive: true, force: true });
  shots.forEach(([name, title, theme], i) =>
    render(phoneShot(lang, name, title, theme), 1080, 1920, join(dir, "phoneScreenshots", `${i + 1}_${name}.png`)),
  );
  render(banner(lang, taglines[lang], 1024, 500), 1024, 500, join(dir, "featureGraphic.png"));
}

for (const [lang, shots] of Object.entries(tablets)) {
  const dir = join(fastlane(locales[lang]), "tenInchScreenshots");
  rmSync(dir, { recursive: true, force: true });
  shots.forEach(([name, title, theme], i) =>
    render(tabletShot(lang, name, title, theme), 2560, 1440, join(dir, `${i + 1}_${name}.png`)),
  );
}

render(poster(), 2400, 1260, join(repo, "brand/poster.png"));
render(banner("en", taglines.en, 1200, 630), 1200, 630, join(repo, "web/public/og.png"));
render(
  base(180, 180, icon.replace(/width="512" height="512"/, 'width="180" height="180"')),
  180,
  180,
  join(repo, "web/public/apple-touch-icon.png"),
);

