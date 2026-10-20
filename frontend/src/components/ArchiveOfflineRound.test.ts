import {describe, expect, it} from 'vitest';
import {storedSelection} from './ArchiveOfflineRound';

describe('storedSelection', () => {
    it('restores a submitted round', () => {
        expect(storedSelection({results: {2: {selectedLabel: '1-13'}}}, 2))
            .toEqual({selectedLabel: '1-13', submitted: true});
    });

    it('clears the selection after the shared day is reset', () => {
        expect(storedSelection(null, 2)).toEqual({selectedLabel: null, submitted: false});
        expect(storedSelection({results: {}}, 2)).toEqual({selectedLabel: null, submitted: false});
    });
});
