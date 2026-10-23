// Retakes every app screenshot the website and the Play graphics use, from the demo on an
// emulator, then puts them all on one contact sheet to check at a glance.
//
//   node brand/store/capture.mjs            (all of them)
//   node brand/store/capture.mjs en nl      (only these groups: en, nl, tablet)
//
// Needs the debug build installed on a resizable emulator (its tablet size is used for the tablet
// shots) and `chromium` for the sheet. ANDROID_SERIAL picks the device; by default the first
// emulator. Writes screens/<lang>/<name>.png and contact-sheet.png (not kept in git); afterwards
// run render.mjs for the Play graphics, and rebuild the website.
//
// Each group of shots starts from a fresh demo (so the news is unread again) with its language,
// theme and look set through DemoActivity's extras. The screens are reached by tapping the labels
// uiautomator finds, in English or Dutch.

import { execFileSync } from "node:child_process";
import { existsSync, mkdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { pathToFileURL } from "node:url";
import { font, here, renderHtml, screensDir } from "./chromium.mjs";

const app = "be.tolly.debug";

// Labels the script taps, per language (the app's own strings).
const labels = {
  en: { courses: "Courses", agenda: "Agenda", news: "News", back: "Back", expand: "Expand", notNow: "Not now" },
  nl: { courses: "Vakken", agenda: "Agenda", news: "Nieuws", back: "Terug", expand: "Uitvouwen", notNow: "Niet nu" },
};

// What to capture: each group is one fresh demo; `shots` maps a file name to a screen.
const groups = [
  { tag: "en", lang: "en", device: "phone", theme: "LIGHT", glass: false, agenda: "LIST",
    shots: { "m-light-courses": "courses", "m-light-course": "course", "m-light-agenda": "agenda", "m-light-news": "news" } },
  { tag: "en", lang: "en", device: "phone", theme: "DARK", glass: false, agenda: "LIST",
    shots: { "m-dark-courses": "courses", "m-dark-course": "course", "m-dark-agenda": "agenda", "m-dark-news": "news" } },
  { tag: "en", lang: "en", device: "phone", theme: "LIGHT", glass: true, agenda: "LIST",
    shots: { "g-light-courses": "courses" } },
  { tag: "en", lang: "en", device: "phone", theme: "DARK", glass: true, agenda: "LIST",
    shots: { "g-dark-courses": "courses", "g-dark-course": "course" } },
  { tag: "nl", lang: "nl", device: "phone", theme: "LIGHT", glass: false, agenda: "LIST",
    shots: { "m-light-courses": "courses", "m-light-course": "course", "m-light-agenda": "agenda", "m-light-news": "news" } },
  { tag: "nl", lang: "nl", device: "phone", theme: "DARK", glass: true, agenda: "LIST",
    shots: { "g-dark-courses": "courses", "g-dark-course": "course" } },
  { tag: "tablet", lang: "en", device: "tablet", theme: "LIGHT", glass: true, agenda: "WEEK",
    shots: { "t-g-light-courses": "course", "t-g-light-agenda": "agenda" } },
  { tag: "tablet", lang: "en", device: "tablet", theme: "LIGHT", glass: false, agenda: "WEEK",
    shots: { "t-m-light-courses": "course" } },
  { tag: "tablet", lang: "en", device: "tablet", theme: "DARK", glass: false, agenda: "WEEK",
    shots: { "t-m-dark-agenda": "agenda", "t-m-dark-courses": "course" } },
  { tag: "tablet", lang: "nl", device: "tablet", theme: "LIGHT", glass: true, agenda: "WEEK",
    shots: { "t-g-light-courses": "course", "t-g-light-agenda": "agenda" } },
  { tag: "tablet", lang: "nl", device: "tablet", theme: "LIGHT", glass: false, agenda: "WEEK",
    shots: { "t-m-light-courses": "course" } },
  { tag: "tablet", lang: "nl", device: "tablet", theme: "DARK", glass: false, agenda: "WEEK",
    shots: { "t-m-dark-agenda": "agenda" } },
];

// ---- adb ----

const serial =
  process.env.ANDROID_SERIAL ||
  execFileSync("adb", ["devices"], { encoding: "utf8" })
    .split("\n")
    .map((line) => line.split("\t"))
    .find(([id, state]) => id.startsWith("emulator-") && state === "device")?.[0];
if (!serial) throw new Error("No emulator found; start one, or set ANDROID_SERIAL.");

const adb = (...args) => execFileSync("adb", ["-s", serial, ...args], { encoding: "utf8", maxBuffer: 64 << 20 });
const shell = (...args) => adb("shell", ...args);
const sleep = (ms) => Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, ms);

if (!shell("pm", "list", "packages", app).includes(app)) {
  throw new Error(`${app} isn't installed: run ./gradlew installDebug in android/ first.`);
}

/**
 * The id of the built-in display (HWC display 0), for screencap: a resizable emulator has a second
 * one, switched off and black, and it isn't always listed last.
 */
function displayId() {
  const list = shell("dumpsys", "SurfaceFlinger", "--display-id");
  return (list.match(/Display (\d+) \(HWC display 0\)/) ?? list.match(/Display (\d+)/))?.[1];
}

/** A clean status bar: 9:41, full battery and Wi-Fi, no notification icons. */
function demoStatusBar() {
  const demo = (...extras) => shell("am", "broadcast", "-a", "com.android.systemui.demo", ...extras);
  shell("settings", "put", "global", "sysui_demo_allowed", "1");
  demo("-e", "command", "exit");
  demo("-e", "command", "enter");
  demo("-e", "command", "clock", "-e", "hhmm", "0941");
  demo("-e", "command", "battery", "-e", "level", "100", "-e", "plugged", "false");
  demo("-e", "command", "network", "-e", "mobile", "hide");
  demo("-e", "command", "network", "-e", "wifi", "show", "-e", "level", "4", "-e", "fully", "true");
  demo("-e", "command", "notifications", "-e", "visible", "false");
}

/** Phone (0) or tablet (2) size, on a resizable emulator. */
function resize(preset) {
  const out = adb("emu", "resize-display", String(preset));
  if (!out.includes("OK")) throw new Error(`The emulator can't resize (${out.trim()}); use a resizable AVD.`);
  sleep(4000);
  demoStatusBar();
}

// ---- the UI, through uiautomator ----

/** The visible nodes with a text or description, and their centres. */
function nodes() {
  shell("uiautomator", "dump", "/sdcard/tolly-ui.xml");
  const xml = shell("cat", "/sdcard/tolly-ui.xml");
  const found = [];
  for (const m of xml.matchAll(/<node [^>]*?text="([^"]*)"[^>]*?content-desc="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/g)) {
    const [, text, desc, x1, y1, x2, y2] = m;
    if (text || desc) found.push({ text, desc, x: (+x1 + +x2) >> 1, y: (+y1 + +y2) >> 1 });
  }
  return found;
}

const matches = (node, label) => node.text === label || node.desc === label;

/** Waits until a node labelled `label` shows, and returns it (or null after `timeout` ms). */
function waitFor(label, timeout = 15000) {
  const end = Date.now() + timeout;
  do {
    const node = nodes().find((n) => matches(n, label));
    if (node) return node;
    sleep(500);
  } while (Date.now() < end);
  return null;
}

function tap(label, { required = true } = {}) {
  const node = waitFor(label, required ? 15000 : 1500);
  if (!node) {
    if (required) throw new Error(`Couldn't find "${label}" on screen.`);
    return false;
  }
  shell("input", "tap", String(node.x), String(node.y));
  return true;
}

function capture(file) {
  sleep(1500); // let animations settle
  const png = execFileSync("adb", ["-s", serial, "exec-out", "screencap", "-p", "-d", displayId()], { maxBuffer: 64 << 20 });
  writeFileSync(file, png);
  console.log(file.replace(here + "/", "brand/store/"));
}

// ---- the screens ----

const course = "Logica voor informatici";

function goTo(screen, l, device) {
  switch (screen) {
    case "courses":
      tap(l.courses);
      break;
    case "course":
      tap(l.courses);
      tap(course);
      sleep(1500);
      // Open the first learning module, so the content shows files.
      tap(l.expand, { required: false });
      break;
    case "agenda":
      tap(l.agenda);
      sleep(1000);
      break;
    case "news":
      tap(l.news);
      sleep(1000);
      tap(l.notNow, { required: false });
      break;
  }
}

/** After a shot, back to where the next one can start (a phone's course is a page of its own). */
function leave(screen, l, device) {
  if (screen === "course" && device === "phone") tap(l.back, { required: false });
}

function startDemo(group) {
  shell("am", "force-stop", app);
  shell("cmd", "locale", "set-app-locales", app, "--locales", group.lang);
  shell(
    "am", "start", "-n", `${app}/be.tolly.DemoActivity`,
    "--es", "theme", group.theme,
    "--ez", "glass", String(group.glass),
    "--ez", "dynamic", "false",
    "--es", "agenda", group.agenda,
    "--ez", "quiet", "true",
  );
  if (!waitFor(labels[group.lang].courses, 30000)) throw new Error("The demo didn't start.");
  sleep(1500);
}

// ---- run ----

const only = process.argv.slice(2);
const todo = groups.filter((g) => only.length === 0 || only.includes(g.tag));
const taken = [];
let size = null;

demoStatusBar();
for (const group of todo) {
  if (group.device !== size) {
    resize(group.device === "tablet" ? 2 : 0);
    size = group.device;
  }
  startDemo(group);
  const l = labels[group.lang];
  mkdirSync(join(screensDir, group.lang), { recursive: true });
  for (const [name, screen] of Object.entries(group.shots)) {
    goTo(screen, l, group.device);
    const file = join(screensDir, group.lang, `${name}.png`);
    capture(file);
    taken.push({ file, label: `${group.lang}/${name}`, tablet: group.device === "tablet" });
    leave(screen, l, group.device);
  }
}

// Back to how the emulator was: phone size, normal status bar, the app in the phone's language
// and its default look.
if (size !== "phone") resize(0);
shell("am", "broadcast", "-a", "com.android.systemui.demo", "-e", "command", "exit");
shell("am", "force-stop", app);
shell("cmd", "locale", "set-app-locales", app, "--locales", "");
shell("am", "start", "-n", `${app}/be.tolly.DemoActivity`, "--es", "theme", "LIGHT", "--ez", "glass", "false", "--ez", "dynamic", "true", "--es", "agenda", "LIST");

// One image with every shot, labelled, to look over instead of opening each one.
const phones = taken.filter((t) => !t.tablet);
const tablets = taken.filter((t) => t.tablet);
const cell = (t, width) =>
  `<figure><img src="${pathToFileURL(t.file).href}" style="width:${width}px"><figcaption>${t.label}</figcaption></figure>`;
const cols = 7, phoneW = 220, tabletW = 500, gap = 16;
const width = cols * phoneW + (cols + 1) * gap;
const phoneRows = Math.ceil(phones.length / cols);
const tabletRows = Math.ceil(tablets.length / 3);
const height = gap + phoneRows * (phoneW * 2400 / 1080 + 40) + tabletRows * (tabletW * 1200 / 1920 + 40) + gap * 2;
renderHtml(
  `<!doctype html><html><head><meta charset="utf-8"><style>
    @font-face { font-family: Jakarta; src: url("${font}"); }
    body { margin: 0; padding: ${gap}px; background: #d9d9e3; font: 600 15px Jakarta, sans-serif; }
    .grid { display: flex; flex-wrap: wrap; gap: ${gap}px; margin-bottom: ${gap}px; }
    figure { margin: 0; } img { display: block; border-radius: 8px; box-shadow: 0 0 0 1px #0002; }
    figcaption { padding-top: 6px; }
  </style></head><body>
    <div class="grid">${phones.map((t) => cell(t, phoneW)).join("")}</div>
    <div class="grid">${tablets.map((t) => cell(t, tabletW)).join("")}</div>
  </body></html>`,
  width,
  Math.ceil(height),
  join(here, "contact-sheet.png"),
);
if (!existsSync(join(here, "contact-sheet.png"))) throw new Error("The contact sheet wasn't made.");
