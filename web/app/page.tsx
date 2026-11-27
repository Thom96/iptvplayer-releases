import { notFound } from "next/navigation";

/** The server only offers an API for the app (see `app/api`), no pages. */
export default function Home() {
  notFound();
}
