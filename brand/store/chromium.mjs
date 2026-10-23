// Shared by render.mjs and capture.mjs: paths, and screenshotting an HTML page with headless
// Chromium (`chromium` on PATH) at an exact size.

import { execFileSync } from "node:child_process";
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

export const here = dirname(fileURLToPath(import.meta.url));
export const repo = resolve(here, "../..");
/** The raw demo screenshots: screens/<lang>/<name>.png. */
export const screensDir = join(here, "screens");
export const font = pathToFileURL(
  join(repo, "web/node_modules/@fontsource-variable/plus-jakarta-sans/files/plus-jakarta-sans-latin-wght-normal.woff2"),
).href;

/** Screenshots `html` at width × height into `out`. */
export function renderHtml(html, width, height, out) {
  const work = mkdtempSync(join(tmpdir(), "tolly-store-"));
  try {
    const page = join(work, "page.html");
    writeFileSync(page, html);
    mkdirSync(dirname(out), { recursive: true });
    execFileSync(
      "chromium",
      [
        "--headless=new",
        "--disable-gpu",
        "--hide-scrollbars",
        "--no-first-run",
        "--force-device-scale-factor=1",
        `--user-data-dir=${join(work, "profile")}`,
        "--allow-file-access-from-files",
        "--virtual-time-budget=3000",
        `--window-size=${width},${height}`,
        `--screenshot=${out}`,
        pathToFileURL(page).href,
      ],
      { stdio: "ignore" },
    );
  } finally {
    rmSync(work, { recursive: true, force: true });
  }
  console.log(out.replace(repo + "/", ""));
}
