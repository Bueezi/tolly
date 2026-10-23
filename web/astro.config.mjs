// @ts-check
import { defineConfig } from "astro/config";

// A fully static site: `npm run build` writes plain HTML, CSS and images to dist/, which
// Cloudflare (Pages or Workers static assets) serves as it is.
export default defineConfig({
  site: "https://tolly.be",
  // Pages are files (privacy.html), which Cloudflare serves at /privacy and redirects /privacy/ to.
  trailingSlash: "never",
  i18n: {
    defaultLocale: "en",
    locales: ["en", "nl"],
    routing: { prefixDefaultLocale: false },
  },
  build: { format: "file" },
  devToolbar: { enabled: false },
  // The app screenshots live in ../brand/store/screens (shared with the Play graphics); let the
  // dev server read them.
  vite: { server: { fs: { allow: [".."] } } },
});
