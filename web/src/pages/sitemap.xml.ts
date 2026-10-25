import type { APIRoute } from "astro";
import { languages, pathFor, type Lang } from "../i18n/ui";

// The pages worth finding in a search, in both languages, each listing its other-language
// version. The privacy policy and /alpha are left out (and noindex): people reach them by link.
const pages = [""];
const langs = Object.keys(languages) as Lang[];

export const GET: APIRoute = ({ site }) => {
  const url = (lang: Lang, page: string) => new URL(pathFor(lang, page), site).href;
  const entries = pages.flatMap((page) =>
    langs.map(
      (lang) => `  <url>
    <loc>${url(lang, page)}</loc>
${langs.map((alt) => `    <xhtml:link rel="alternate" hreflang="${alt}" href="${url(alt, page)}"/>`).join("\n")}
  </url>`,
    ),
  );
  const xml = `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:xhtml="http://www.w3.org/1999/xhtml">
${entries.join("\n")}
</urlset>
`;
  return new Response(xml, { headers: { "Content-Type": "application/xml" } });
};
