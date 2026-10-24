import { links, pathFor, strings, type Lang } from "./ui";

/** Describes Tolly as an app (schema.org SoftwareApplication), so search results can show it as one. */
export function appJsonLd(lang: Lang, site: URL) {
  const t = strings(lang);
  return {
    "@context": "https://schema.org",
    "@type": "SoftwareApplication",
    name: "Tolly",
    alternateName: lang === "nl" ? "Tolly voor Toledo" : "Tolly for Toledo",
    description: t.meta.description,
    url: new URL(pathFor(lang), site).href,
    image: new URL("/og.png", site).href,
    inLanguage: lang,
    operatingSystem: "Android 8.0+",
    applicationCategory: "EducationalApplication",
    downloadUrl: links.apk,
    license: "https://www.gnu.org/licenses/agpl-3.0.html",
    isAccessibleForFree: true,
    offers: { "@type": "Offer", price: "0", priceCurrency: "EUR" },
    author: { "@type": "Person", name: "Bueezi", email: links.email },
    codeRepository: links.repo,
  };
}
