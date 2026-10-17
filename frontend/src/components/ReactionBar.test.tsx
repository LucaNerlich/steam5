// @vitest-environment happy-dom
// The Escape-refocus wiring runs in effects against a live document, so this file
// needs a DOM; the static-markup tests here are env-agnostic and run under it too.
// (Vitest 4 removed `environmentMatchGlobs`, so the per-file docblock is the hook.)
import {act} from 'react';
import {createRoot} from 'react-dom/client';
import {describe, expect, it} from 'vitest';
import {renderToStaticMarkup} from 'react-dom/server';
import ReactionBar from './ReactionBar';
import type {CommentReactionDto} from '@/lib/comments';

function reaction(overrides: Partial<CommentReactionDto> = {}): CommentReactionDto {
    return {
        reactionType: 'THUMBS_UP',
        count: 2,
        reactedByViewer: false,
        reactors: ['Alice', 'Bob'],
        ...overrides,
    };
}

describe('ReactionBar reactor tooltip', () => {
    it('sets the title on the read-only chip to the joined reactor names', () => {
        const html = renderToStaticMarkup(
            <ReactionBar
                commentId={1}
                reactions={[reaction()]}
                canReact={false}
                onToggled={() => {}}
                readOnly
            />,
        );

        expect(html).toContain('title="Alice, Bob"');
    });

    it('appends an overflow note when count exceeds the returned reactor names', () => {
        const html = renderToStaticMarkup(
            <ReactionBar
                commentId={1}
                reactions={[reaction({count: 5, reactors: ['Alice', 'Bob']})]}
                canReact={false}
                onToggled={() => {}}
                readOnly
            />,
        );

        expect(html).toContain('title="Alice, Bob and 3 more"');
    });

    it('omits the title attribute on the read-only chip when there are no resolved reactor names', () => {
        const html = renderToStaticMarkup(
            <ReactionBar
                commentId={1}
                reactions={[reaction({reactors: []})]}
                canReact={false}
                onToggled={() => {}}
                readOnly
            />,
        );

        expect(html).not.toContain('title=');
    });

    it('combines reactor names with the action hint in the interactive chip title', () => {
        const html = renderToStaticMarkup(
            <ReactionBar
                commentId={1}
                reactions={[reaction()]}
                canReact={true}
                onToggled={() => {}}
            />,
        );

        expect(html).toContain('title="Add reaction — Alice, Bob"');
    });

    it('appends the overflow note to the interactive chip title too', () => {
        const html = renderToStaticMarkup(
            <ReactionBar
                commentId={1}
                reactions={[reaction({count: 4, reactedByViewer: true, reactors: ['Alice', 'Bob']})]}
                canReact={true}
                onToggled={() => {}}
            />,
        );

        expect(html).toContain('title="Remove reaction — Alice, Bob and 2 more"');
    });

    it('falls back to only the action hint on the interactive chip when there are no reactor names', () => {
        const html = renderToStaticMarkup(
            <ReactionBar
                commentId={1}
                reactions={[reaction({reactors: []})]}
                canReact={false}
                onToggled={() => {}}
            />,
        );

        expect(html).toContain('title="Sign in to react"');
    });

    it('keeps the action hint available through aria-label on the interactive chip, alongside reactor names', () => {
        const html = renderToStaticMarkup(
            <ReactionBar
                commentId={1}
                reactions={[reaction()]}
                canReact={false}
                onToggled={() => {}}
            />,
        );

        expect(html).toContain('aria-label="👍 reaction, 2 (sign in to react) — Alice, Bob"');
    });

    it('omits the reactor-name suffix from aria-label on the interactive chip when there are none', () => {
        const html = renderToStaticMarkup(
            <ReactionBar
                commentId={1}
                reactions={[reaction({reactors: []})]}
                canReact={false}
                onToggled={() => {}}
            />,
        );

        expect(html).toContain('aria-label="👍 reaction, 2 (sign in to react)"');
    });

    it('exposes reactor names through aria-label on the read-only chip too, since native title tooltips are not reliably announced by screen readers or shown on touch', () => {
        const html = renderToStaticMarkup(
            <ReactionBar
                commentId={1}
                reactions={[reaction({count: 5, reactors: ['Alice', 'Bob']})]}
                canReact={false}
                onToggled={() => {}}
                readOnly
            />,
        );

        expect(html).toContain('aria-label="👍 reaction, 5 — Alice, Bob and 3 more"');
    });
});

describe('ReactionBar Escape refocus', () => {
    // React 19's act() only drives effects when the test environment is declared.
    (globalThis as {IS_REACT_ACT_ENVIRONMENT?: boolean}).IS_REACT_ACT_ENVIRONMENT = true;

    it('puts focus back on the trigger when Escape is pressed inside the open picker', async () => {
        const container = document.createElement('div');
        document.body.appendChild(container);
        const root = createRoot(container);
        try {
            await act(async () => {
                root.render(
                    <ReactionBar
                        commentId={1}
                        reactions={[]}
                        canReact={true}
                        onToggled={() => {}}
                        open={true}
                        onPickerOpenChange={() => {}}
                    />,
                );
            });

            const option = container.querySelector<HTMLButtonElement>('[role="menuitemcheckbox"]');
            const trigger = container.querySelector<HTMLButtonElement>('button[aria-haspopup="menu"]');
            if (!option || !trigger) throw new Error('reaction picker did not render');

            option.focus();
            expect(document.activeElement).toBe(option);

            await act(async () => {
                document.dispatchEvent(new KeyboardEvent('keydown', {key: 'Escape', bubbles: true}));
            });

            expect(document.activeElement).toBe(trigger);
        } finally {
            await act(async () => root.unmount());
            container.remove();
        }
    });
});
