'use client'

/**
 * Root error boundary.
 *
 * Next hands the boundary the thrown error and a `reset` callback that re-renders the
 * failed segment. This previously took neither, so it was a dead end: any thrown render
 * (a transient upstream 500, for instance) left the visitor on "500 - Server Error" with
 * no in-app recovery and nothing recorded client-side.
 *
 * `error` is part of the contract but deliberately not rendered — it can carry internals,
 * and `console.*` is stripped from production builds by `compiler.removeConsole` in
 * next.config.ts, so logging here would be a no-op where it matters.
 */
export default function Error({reset}: {error: Error & {digest?: string}; reset: () => void}) {
    return (
        <section className="container">
            <h1>500 - Server Error</h1>
            <p>Something went wrong while loading this page. You can try again.</p>
            <button type="button" className="btn-cta" onClick={() => reset()}>
                Try again
            </button>
        </section>
    )
}
