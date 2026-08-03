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

    it('keeps the action hint available through aria-label on the interactive chip', () => {
        const html = renderToStaticMarkup(
            <ReactionBar
                commentId={1}
                reactions={[reaction()]}
                canReact={false}
                onToggled={() => {}}
            />,
        );

        expect(html).toContain('aria-label="👍 reaction, 2 (sign in to react)"');
    });
});
