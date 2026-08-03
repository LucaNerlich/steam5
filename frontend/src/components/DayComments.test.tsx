import type {ReactNode} from 'react';
import {describe, expect, it, vi} from 'vitest';
import {renderToStaticMarkup} from 'react-dom/server';

// Avoid pulling in Next's App Router context / request-scoped APIs for a plain
// string-matching render test — mirrors the mocking approach used by actions.test.ts.
vi.mock('next/headers', () => ({
    cookies: async () => ({get: () => undefined}),
}));
vi.mock('next/link', () => ({
    default: ({href, className, children}: {href: string; className?: string; children?: ReactNode}) => (
        <a href={href} className={className}>{children}</a>
    ),
}));

import {CommentBodyText} from './DayComments';

function render(body: string): string {
    return renderToStaticMarkup(<CommentBodyText body={body}/>);
}

describe('CommentBodyText mentions', () => {
    it('renders a mention token as a link to the profile page', () => {
        const html = render('Hey [@Alice](mention:76500000000000001), nice guess!');

        expect(html).toContain('href="/profile/76500000000000001"');
        expect(html).toContain('@Alice');
    });

    it('preserves surrounding plain text around a mention', () => {
        const html = render('Hey [@Alice](mention:76500000000000001), nice guess!');

        expect(html).toContain('Hey ');
        expect(html).toContain(', nice guess!');
    });

    it('strips brackets from the mention label for defense in depth', () => {
        // The label capture group allows '[' (only ']' terminates it), so a stray
        // '[' can still reach sanitizeMentionLabel, which strips it before rendering.
        const html = render('cc [@Al[ice](mention:76500000000000001)');

        expect(html).not.toContain('Al[ice');
        expect(html).toContain('@Alice');
    });

    it('renders plain text with no links when there are no tokens', () => {
        const html = render('just a normal comment, no refs here');

        expect(html).not.toContain('<a');
        expect(html).toContain('just a normal comment, no refs here');
    });

    it('renders both a game link and a mention together in caret order', () => {
        const html = render(
            'ping [@Alice](mention:76500000000000001) about [Half-Life 2](https://store.steampowered.com/app/220)',
        );

        expect(html).toContain('href="/profile/76500000000000001"');
        expect(html).toContain('href="https://store.steampowered.com/app/220"');
        const mentionIndex = html.indexOf('/profile/76500000000000001');
        const gameIndex = html.indexOf('/app/220');
        expect(mentionIndex).toBeGreaterThan(-1);
        expect(gameIndex).toBeGreaterThan(mentionIndex);
    });
});
