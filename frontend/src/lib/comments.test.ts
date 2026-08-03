import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {
    COMMENT_MODERATOR_STEAM_ID,
    REACTION_EMOJI,
    REACTION_TYPES,
    archiveComment,
    commentMutationError,
    commentsUrl,
    fetchComments,
    searchMentionCandidates,
    steamStoreUrl,
    toggleReaction,
    type DayComment,
    type MentionCandidate,
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

    it('maps ApiError.message codes for non-OK responses', async () => {
        fetchMock.mockResolvedValue(mockResponse(false, {
            error: 'Bad Request',
            message: 'not_current_game_day',
        }, 400));

        await expect(toggleReaction(42, 'HUG')).rejects.toThrow('not_current_game_day');
    });
});

describe('archiveComment', () => {
    it('POSTs to the archive proxy', async () => {
        fetchMock.mockResolvedValue(mockResponse(true, {ok: true}, 200));

        await archiveComment(42);

        expect(fetchMock).toHaveBeenCalledWith('/api/review-game/comments/archive/42', {
            method: 'POST',
            headers: {accept: 'application/json'},
            cache: 'no-store',
        });
    });

    it('throws unauthorized for 401 responses', async () => {
        fetchMock.mockResolvedValue(mockResponse(false, {error: 'Unauthorized'}, 401));

        await expect(archiveComment(42)).rejects.toThrow('unauthorized');
    });
});

describe('commentMutationError', () => {
    it('normalizes unauthorized responses', () => {
        expect(commentMutationError(401, {error: 'unauthenticated'}, 'fallback').message)
            .toBe('unauthorized');
        expect(commentMutationError(403, {error: 'Unauthorized'}, 'fallback').message)
            .toBe('unauthorized');
    });

    it('prefers snake_case ApiError.message codes', () => {
        expect(commentMutationError(400, {
            error: 'Bad Request',
            message: 'not_current_game_day',
        }, 'fallback').message).toBe('not_current_game_day');
    });

    it('falls back when no machine code is present', () => {
        expect(commentMutationError(500, {}, 'Failed to archive comment').message)
            .toBe('Failed to archive comment');
    });
});

describe('COMMENT_MODERATOR_STEAM_ID', () => {
    it('matches the hardcoded moderator account', () => {
        expect(COMMENT_MODERATOR_STEAM_ID).toBe('76561198028075069');
    });
});

describe('steamStoreUrl', () => {
    it('builds the Steam store app URL', () => {
        expect(steamStoreUrl(620)).toBe('https://store.steampowered.com/app/620');
    });
});

describe('MentionCandidate', () => {
    it('supports steamId, personaName, and an optional avatar', () => {
        const withAvatar: MentionCandidate = {steamId: 'u1', personaName: 'Alice', avatar: 'https://x/a.jpg'};
        const withoutAvatar: MentionCandidate = {steamId: 'u2', personaName: 'Bob'};

        expect(withAvatar.steamId).toBe('u1');
        expect(withoutAvatar.avatar).toBeUndefined();
    });
});

describe('searchMentionCandidates', () => {
    it('GETs the users search proxy with cache no-store and returns parsed candidates', async () => {
        const candidates: MentionCandidate[] = [{steamId: 'u1', personaName: 'Alice', avatar: null}];
        fetchMock.mockResolvedValue(mockResponse(true, candidates));

        const result = await searchMentionCandidates('ali');

        expect(result).toEqual(candidates);
        expect(fetchMock).toHaveBeenCalledWith('/api/users/search?q=ali', {cache: 'no-store'});
    });

    it('encodes special characters in the query', async () => {
        fetchMock.mockResolvedValue(mockResponse(true, []));

        await searchMentionCandidates('a b&c');

        expect(fetchMock).toHaveBeenCalledWith('/api/users/search?q=a%20b%26c', {cache: 'no-store'});
    });

    it('returns an empty list rather than throwing when the proxy responds non-OK', async () => {
        fetchMock.mockResolvedValue(mockResponse(false, {error: 'boom'}));

        const result = await searchMentionCandidates('ali');

        expect(result).toEqual([]);
    });
});
