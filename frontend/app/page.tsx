// This page never renders: next.config.ts permanently redirects '/' to
// '/review-guesser/1'. It exists only to own the route.
export default function Home() {
    return null;
}
