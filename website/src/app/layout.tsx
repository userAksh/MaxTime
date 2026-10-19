import type { Metadata } from "next";
import { MotionProvider } from "@/components/MotionProvider";
import "./globals.css";

const title = "MaxTime — Let's make it count.";
const description =
  "Discover things you love doing, find people who are up for it, and turn free time into plans worth making. Go solo, join a group, or invite friends.";

export const metadata: Metadata = {
  metadataBase: new URL("https://maxtime.in"),
  title,
  description,
  icons: {
    icon: [
      { url: "/favicon-32.png", sizes: "32x32", type: "image/png" },
      { url: "/favicon-16.png", sizes: "16x16", type: "image/png" },
    ],
    apple: "/apple-touch-icon.png",
  },
  openGraph: {
    title,
    description,
    url: "https://maxtime.in",
    siteName: "MaxTime",
    images: [{ url: "/brand/mark-1024.png", width: 1024, height: 1024 }],
    locale: "en_IN",
    type: "website",
  },
  twitter: {
    card: "summary",
    title,
    description,
    images: ["/brand/mark-1024.png"],
  },
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en" className="h-full">
      <body className="min-h-full bg-bg-1 text-text antialiased">
        <MotionProvider>{children}</MotionProvider>
      </body>
    </html>
  );
}
