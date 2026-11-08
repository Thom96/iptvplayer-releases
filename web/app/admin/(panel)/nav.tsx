"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import styles from "./admin.module.css";

const ITEMS = [
  { href: "/admin", label: "Dashboard" },
  { href: "/admin/apparaten", label: "Apparaten" },
  { href: "/admin/betalingen", label: "Betalingen" },
  { href: "/admin/instellingen", label: "Instellingen" },
];

export default function Nav() {
  const path = usePathname();
  return (
    <>
      {ITEMS.map((item) => {
        const on = item.href === "/admin" ? path === "/admin" : path.startsWith(item.href);
        return (
          <Link key={item.href} href={item.href} className={on ? `${styles.link} ${styles.on}` : styles.link} aria-current={on ? "page" : undefined}>
            {item.label}
          </Link>
        );
      })}
    </>
  );
}
