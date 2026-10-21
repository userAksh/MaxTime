import { onRequest } from "firebase-functions/v2/https";
import { db } from "./admin";
import { getActiveReferralConfig } from "./referral";

// Real, deployed custom domain for the "website" Hosting target — see
// website/src/app/layout.tsx's metadataBase. Not read from config at request time since this
// function has no dependency on the website's Next.js build; it's just the one fact both share.
const SITE_URL = "https://maxtime.in";
const OG_IMAGE_URL = `${SITE_URL}/brand/mark-1024.png`;

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function renderPage(params: {
  title: string;
  description: string;
  canonicalUrl: string;
  code: string | null;
}): string {
  const { title, description, canonicalUrl, code } = params;
  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>${escapeHtml(title)}</title>
<meta name="description" content="${escapeHtml(description)}" />
<meta property="og:type" content="website" />
<meta property="og:site_name" content="MaxTime" />
<meta property="og:title" content="${escapeHtml(title)}" />
<meta property="og:description" content="${escapeHtml(description)}" />
<meta property="og:image" content="${OG_IMAGE_URL}" />
<meta property="og:url" content="${escapeHtml(canonicalUrl)}" />
<meta name="twitter:card" content="summary_large_image" />
<style>
  body { margin:0; min-height:100vh; display:flex; align-items:center; justify-content:center;
    background:#040B06; color:#F3FFEA; font-family:-apple-system,system-ui,sans-serif;
    text-align:center; padding:24px; }
  .card { max-width:420px; }
  .mark { width:72px; height:72px; border-radius:20px; margin-bottom:24px; }
  h1 { font-size:24px; margin:0 0 12px; }
  p { color:#9CB89F; font-size:15px; line-height:1.5; margin:0 0 28px; }
  .code { display:inline-block; font-weight:800; letter-spacing:0.08em; background:#0E2413;
    border:1px solid #1C3B22; color:#C6FF3D; padding:12px 20px; border-radius:14px; font-size:18px;
    margin-bottom:28px; }
  a.cta { display:inline-block; background:#C6FF3D; color:#06130A; font-weight:700;
    padding:14px 28px; border-radius:999px; text-decoration:none; }
</style>
</head>
<body>
  <div class="card">
    <img class="mark" src="${OG_IMAGE_URL}" alt="MaxTime" />
    <h1>${escapeHtml(title)}</h1>
    <p>${escapeHtml(description)}</p>
    ${code ? `<div class="code">${escapeHtml(code)}</div>` : ""}
    <div><a class="cta" href="${SITE_URL}">Get MaxTime</a></div>
  </div>
</body>
</html>`;
}

/**
 * Public, unauthenticated referral share-link landing page — `GET /r/:code`, reached via a
 * Firebase Hosting rewrite on the "website" target (see firebase.json). Exists so WhatsApp (and
 * any other chat app that unfurls links) can render a real Open Graph preview card for a referral
 * share, something no amount of image-only sharing can produce — see the conversation that added
 * this for the HYPD/WhatsApp reference example that prompted it.
 *
 * Deliberately does NOT look up or expose the referrer's name/photo — `referral_codes/{code}`
 * only stores `ownerUserId`, and this page never reads the corresponding `users/{uid}` doc, so a
 * stranger's identity is never shown to whoever opens an unauthenticated public link. Matches the
 * app's existing sensitive-data posture (see docs/analytics/event-taxonomy.md §4) applied to a new
 * surface. An invalid/expired/unknown code renders a generic MaxTime card instead of an error, so
 * a stale or mistyped link still looks intentional rather than broken.
 */
export const referralLandingPage = onRequest(async (req, res) => {
  const match = req.path.match(/\/r\/([A-Za-z0-9]+)/);
  const rawCode = match?.[1]?.toUpperCase() ?? null;

  let validCode: string | null = null;
  if (rawCode) {
    const snap = await db.collection("referral_codes").doc(rawCode).get();
    if (snap.exists && snap.get("status") === "ACTIVE") {
      validCode = rawCode;
    }
  }

  let title: string;
  let description: string;
  if (validCode) {
    const config = await getActiveReferralConfig();
    title = "You're invited to MaxTime!";
    description =
      `Join MaxTime and get ${config.refereeDiscountPercent}% off your first booking ` +
      `(up to ₹${config.refereeDiscountCapAmount}) with code ${validCode}.`;
  } else {
    title = "MaxTime — Real People. Active Lives.";
    description = "Discover real activities and real people near you.";
  }

  const canonicalUrl = `${SITE_URL}${req.path}`;
  res.set("Content-Type", "text/html; charset=utf-8");
  res.status(200).send(renderPage({ title, description, canonicalUrl, code: validCode }));
});
