/**
 * Explicit configuration points for external links. MaxTime isn't published on either app store
 * yet — those buttons render honestly and degrade to a "coming soon" state until filled in here.
 */
export const siteConfig = {
  appStoreUrl: "" as string, // e.g. "https://apps.apple.com/app/id..."
  playStoreUrl: "" as string, // e.g. "https://play.google.com/store/apps/details?id=..."
  instagramUrl: "https://instagram.com/maxtime.social",
  contactEmail: "hello@maxtime.in",
};

export const isConfigured = (url: string) => url.trim().length > 0;
