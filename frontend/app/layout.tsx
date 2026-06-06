import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "PDF 문서 변환",
  description: "PDF를 수정 가능한 문서로 변환하는 서비스",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  );
}
