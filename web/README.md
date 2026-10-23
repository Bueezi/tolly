# Tolly website

The landing page for Tolly, in English (`/`) and Dutch (`/nl/`). It's a static [Astro](https://astro.build)
site: the build writes plain HTML, CSS and images to `dist/`, with no server code, no analytics
and no third-party requests (the font is bundled).

```bash
npm install
npm run dev       # http://localhost:4321
npm run build     # writes dist/
```

## Layout

- `src/i18n/ui.ts`: every string, in English and Dutch. Add both.
- `src/components/Landing.astro`: the page's sections; `src/pages/index.astro` and
  `src/pages/nl/index.astro` render it in each language.
- `src/pages/privacy.md` and `src/pages/nl/privacy.md`: the privacy policy Google Play links to,
  in Markdown with the `Policy.astro` layout. Change both, and their dates, together.
- `src/components/Device.astro`: a screenshot in a phone or tablet frame, served as AVIF/WebP in
  a few widths, with an optional dark-theme screenshot.
- The app screenshots come from `../brand/store/screens/en/`, shared with the Play graphics;
  `node brand/store/capture.mjs` retakes them from the demo on the emulator.
- `public/og.png` and `public/apple-touch-icon.png` come from `node brand/store/render.mjs`, which
  also renders the Play Store graphics.

## Hosting on Cloudflare

Either of these works:

- **Pages:** connect the repository, set the root directory to `web`, the build command to
  `npm run build` and the output directory to `dist`.
- **Workers (static assets):** `npm run deploy`, which builds and runs `wrangler deploy` with
  `wrangler.jsonc`.

`public/_headers` sets security headers and long caching for the hashed files in `/_astro/`.
The canonical URL is `site` in `astro.config.mjs` (`https://tolly.be` for now); change it if the
domain differs.
