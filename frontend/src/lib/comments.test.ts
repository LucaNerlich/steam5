import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {
    REACTION_EMOJI,
    REACTION_TYPES,
    commentsUrl,
    fetchComments,
    toggleReaction,
    type DayComment,
} from './comments';

function mockResponse(ok: boolean, body: unknown, status = ok ? 200 : 500) {
    return {
        ok,
        status,
        json: async () => body,
    } as unknown as Response;
}

let fetchMock: ReturnType<typeof vi.fn>;

beforeEach(() => {
    fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
    vi.unstubAllGlobals();
});

describe('REACTION_TYPES / REACTION_EMOJI', () => {
    it('covers every reaction type with an emoji', () => {
        expect(REACTION_TYPES).toEqual(['THUMBS_UP', 'LAUGH_CRYING', 'LAUGH_TEAR', 'HUG']);
        for (const type of REACTION_TYPES) {
            expect(REACTION_EMOJI[type]).toBeTruthy();
        }
        expect(REACTION_EMOJI.THUMBS_UP).toBe('👍');
        expect(REACTION_EMOJI.LAUGH_CRYING).toBe('😂');
        expect(REACTION_EMOJI.LAUGH_TEAR).toBe('😢');
        expect(REACTION_EMOJI.HUG).toBe('🤗');
    });
});

describe('commentsUrl', () => {
    it('builds the day-scoped proxy path', () => {
        expect(commentsUrl('2026-07-31')).toBe('/api/review-game/comments/2026-07-31');
    });

    it('encodes special characters in the date segment', () => {
        expect(commentsUrl('2026/07/31')).toBe('/api/review-game/comments/2026%2F07%2F31');
    });
});

describe('fetchComments', () => {
    it('GETs the proxy with cache no-store and returns parsed comments', async () => {
        const comments: DayComment[] = [{
            id: 1,
            body: 'hi',
            createdAt: '2026-07-31T12:00:00Z',
            author: {steamId: 'u1', personaName: 'Alice', avatar: null, avatarBlurdata: null},
            reactions: [],
        }];
        fetchMock.mockResolvedValue(mockResponse(true, comments));

        const result = await fetchComments('2026-07-31');

        expect(result).toEqual(comments);
        expect(fetchMock).toHaveBeenCalledWith('/api/review-game/comments/2026-07-31', {cache: 'no-store'});
    });

    it('uses force-cache when fetching immutable archive comments', async () => {
        fetchMock.mockResolvedValue(mockResponse(true, []));

        await fetchComments('2026-07-31', {immutable: true});

        expect(fetchMock).toHaveBeenCalledWith('/api/review-game/comments/2026-07-31', {
            cache: 'force-cache',
        });
    });

    it('throws when the proxy responds non-OK', async () => {
        fetchMock.mockResolvedValue(mockResponse(false, {error: 'boom'}));

        await expect(fetchComments('2026-07-31')).rejects.toThrow('Failed to fetch comments');
    });
});

describe('toggleReaction', () => {
    it('POSTs the reaction type to the reactions proxy', async () => {
        const reactions = [{reactionType: 'THUMBS_UP', count: 1, reactedByViewer: true}];
        fetchMock.mockResolvedValue(mockResponse(true, reactions));

        const result = await toggleReaction(42, 'THUMBS_UP');

        expect(result).toEqual(reactions);
        expect(fetchMock).toHaveBeenCalledWith('/api/review-game/comments/reactions/42', {
            method: 'POST',
            headers: {'content-type': 'application/json', accept: 'application/json'},
            body: JSON.stringify({reactionType: 'THUMBS_UP'}),
            cache: 'no-store',
        });
    });

    it('throws with the API error message when available', async () => {
        fetchMock.mockResolvedValue(mockResponse(false, {error: 'rate_limit_exceeded'}, 429));

        await expect(toggleReaction(42, 'HUG')).rejects.toThrow('rate_limit_exceeded');
    });

    it('throws unauthorized for 401 responses', async () => {
        fetchMock.mockResolvedValue(mockResponse(false, {error: 'unauthenticated'}, 401));

        await expect(toggleReaction(42, 'HUG')).rejects.toThrow('unauthorized');
    });

    it('throws a fallback message when the error body is empty', async () => {
        fetchMock.mockResolvedValue(mockResponse(false, {}));

        await expect(toggleReaction(42, 'HUG')).rejects.toThrow('Failed to toggle reaction');
    });
});
