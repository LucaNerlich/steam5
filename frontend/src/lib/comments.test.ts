import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {
    REACTION_EMOJI,
    REACTION_TYPES,
    commentsUrl,
    fetchComments,
    postComment,
    toggleReaction,
    type DayComment,
} from './comments';

function mockResponse(ok: boolean, body: unknown) {
    return {
        ok,
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

    it('throws when the proxy responds non-OK', async () => {
        fetchMock.mockResolvedValue(mockResponse(false, {error: 'boom'}));

        await expect(fetchComments('2026-07-31')).rejects.toThrow('Failed to fetch comments');
    });
});

describe('postComment', () => {
    it('POSTs JSON body to the day proxy and returns the created comment', async () => {
        const created: DayComment = {
            id: 2,
            body: 'great day',
            createdAt: '2026-07-31T12:00:00Z',
            author: {steamId: 'u1', personaName: 'Alice', avatar: null, avatarBlurdata: null},
            reactions: [],
        };
        fetchMock.mockResolvedValue(mockResponse(true, created));

        const result = await postComment('2026-07-31', 'great day');

        expect(result).toEqual(created);
        expect(fetchMock).toHaveBeenCalledWith('/api/review-game/comments/2026-07-31', {
            method: 'POST',
            headers: {'content-type': 'application/json', accept: 'application/json'},
            body: JSON.stringify({body: 'great day'}),
            cache: 'no-store',
        });
    });

    it('throws with the API error message when available', async () => {
        fetchMock.mockResolvedValue(mockResponse(false, {error: 'day_not_complete'}));

        await expect(postComment('2026-07-31', 'hi')).rejects.toThrow('day_not_complete');
    });

    it('throws a fallback message when the error body has no error field', async () => {
        fetchMock.mockResolvedValue(mockResponse(false, {}));

        await expect(postComment('2026-07-31', 'hi')).rejects.toThrow('Failed to post comment');
    });

    it('throws a fallback message when error JSON parsing fails', async () => {
        fetchMock.mockResolvedValue({
            ok: false,
            json: async () => {
                throw new Error('invalid json');
            },
        } as unknown as Response);

        await expect(postComment('2026-07-31', 'hi')).rejects.toThrow('Failed to post comment');
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
        fetchMock.mockResolvedValue(mockResponse(false, {error: 'rate_limit_exceeded'}));

        await expect(toggleReaction(42, 'HUG')).rejects.toThrow('rate_limit_exceeded');
    });

    it('throws a fallback message when the error body is empty', async () => {
        fetchMock.mockResolvedValue(mockResponse(false, {}));

        await expect(toggleReaction(42, 'HUG')).rejects.toThrow('Failed to toggle reaction');
    });
});
