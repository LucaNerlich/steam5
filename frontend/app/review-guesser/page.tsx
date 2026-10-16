import {redirect} from "next/navigation";

export default function ReviewGuesserEntryPage() {
    redirect('/review-guesser/1');
}

// The metadata export that used to sit here declared a 26-line canonical/OG/keywords block for a
// route that answers 307 before any render (`redirect('/review-guesser/1')` below), so none of it
// could ever be served — and it advertised a canonical for the redirecting URL, the exact dupe the
// sitemap exclusions exist to avoid.
