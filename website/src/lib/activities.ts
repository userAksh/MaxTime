export interface Activity {
  id: string;
  label: string;
  /** Short 1–2 word version for small contexts (hero chips) where the full label won't fit. */
  short: string;
  category: string;
  /** Real emoji, matching how the app itself represents activities (ActivityInfo.icon in
   *  shared/src/commonMain/kotlin/com/maxtime/app/data/MockData.kt) — reused here rather than
   *  invented line icons, which is both more recognizable and more consistent with the product. */
  emoji: string;
  /** Jewel-tone card colour — the exact palette the app's own "Popular near you" promo carousel
   *  uses (HomeScreen.kt's carouselPalette), deliberately distinct from the single lime accent so
   *  each activity reads as its own "product," the same way it does in the app. */
  color: string;
}

const palette = [
  "#2F6B4F", // jade
  "#4A3F7A", // indigo
  "#7C6A1F", // olive gold
  "#8B3E3E", // terracotta
  "#2F5D6B", // teal
];

/** Illustrative categories only — not a live catalogue, no invented listings, ratings or venues. */
export const activities: Activity[] = [
  { id: "padel", label: "Padel & racket sports", short: "Padel", category: "Sports & fitness", emoji: "🎾", color: palette[0] },
  { id: "pottery", label: "Pottery studio", short: "Pottery", category: "Creative workshops", emoji: "🏺", color: palette[1] },
  { id: "music", label: "Live music & jam sessions", short: "Live music", category: "Music", emoji: "🎸", color: palette[2] },
  { id: "trail", label: "Trail run club", short: "Trail run", category: "Outdoors", emoji: "🏃", color: palette[3] },
  { id: "art", label: "Life drawing & art", short: "Life drawing", category: "Classes", emoji: "🎨", color: palette[4] },
  { id: "yoga", label: "Rooftop yoga", short: "Yoga", category: "Wellness", emoji: "🧘", color: palette[0] },
  { id: "games", label: "Board game night", short: "Game night", category: "Social", emoji: "🎲", color: palette[1] },
  { id: "climb", label: "Climbing gym", short: "Climbing", category: "Sports & fitness", emoji: "🧗", color: palette[2] },
];
