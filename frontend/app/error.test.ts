import {describe, expect, it} from 'vitest';
import {createElement} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import ErrorBoundary from './error';

/**
 * The boundary must offer a way back into the app (Next's `reset`) instead of a dead
 * "500 - Server Error" screen. Rendered to static markup because the suite runs in a
 * node environment with no DOM: the handler itself cannot be clicked here, but the
 * retry control's presence is what the boundary was missing.
 */
describe('root error boundary', () => {
    it('renders a retry control instead of a dead end', () => {
        const html = renderToStaticMarkup(
            createElement(ErrorBoundary, {error: new Error('boom'), reset: () => {}}),
        );

        expect(html).toContain('500');
        expect(html).toContain('Try again');
    });

    it('does not leak the error message to the user', () => {
        const html = renderToStaticMarkup(
            createElement(ErrorBoundary, {error: new Error('upstream exploded: secret detail'), reset: () => {}}),
        );

        expect(html).not.toContain('upstream exploded');
    });
});
