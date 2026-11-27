import type { Metadata, Viewport } from "next";

export const metadata: Metadata = {
  title: "Gallo",
  description: "Gallo",
  robots: { index: false, follow: false },
};

export const viewport: Viewport = { themeColor: "#0B1A22", width: "device-width", initialScale: 1 };

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="nl">
      <head>
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="" />
        <link
          rel="stylesheet"
          href="https://fonts.googleapis.com/css2?family=Outfit:wght@400;500;600&family=Young+Serif&display=swap"
        />
      </head>
      <body>{children}</body>
    </html>
  );
}
