// Review Game API types

export interface GuessRequest {
    appId: number;
    bucketGuess: string;
}

export interface GuessResponse {
    appId: number;
    totalReviews: number;
    actualBucket: string;
    correct: boolean;
}

export type BucketsResponse = { buckets: string[]; bucketTitles: string[] } | string[];

export interface ReviewGameState {
    date: string; // ISO local date (yyyy-MM-dd)
    buckets: string[];
    bucketTitles?: string[];
    picks: SteamAppDetail[];
}

// Steam details as returned by backend
export interface SteamAppDetail {
    appId: number;
    type: string | null;
    name: string;
    controllerSupport: string | null;
    isFree: boolean;
    dlc: string | null; // comma separated list of appIds as string
    shortDescription: string | null;
    detailedDescription: string | null;
    aboutTheGame: string | null;
    headerImage: string | null;
    capsuleImage: string | null;
    website: string | null;
    legalNotice: string | null;
    priceOverview: Price | null;
    developers: Developer[];
    publisher: Publisher[];
    categories: Category[];
    windows: boolean;
    mac: boolean;
    linux: boolean;
    genres: Genre[];
    screenshots: Screenshot[];
    movies: Movie[];
    recommendations: number | null;
    releaseDate: string | null;
    backgroundRaw: string | null;
}

export interface Price {
    appId: number;
    currency: string | null;
    initial: number;
    finalAmount: number; // backend field name is finalAmount (final being a reserved word)
    discountPercent: number;
    initialFormatted: string | null;
    finalFormatted: string | null;
}

export interface Developer {
    id: number;
    name: string;
}

export interface Publisher {
    id: number;
    name: string;
}

export interface Category {
    id: number;
    description: string;
}

export interface Genre {
    id: number;
    description: string;
}

export interface Screenshot {
    id: number;
    pathThumbnail: string;
    pathFull: string;
    blurhashThumb?: string | null;
    blurhashFull?: string | null;
    blurdataThumb?: string | null;
    blurdataFull?: string | null;
}

export interface Movie {
    id: number;
    name: string;
    thumbnail: string;
    webm: string | null;
    mp4: string | null;
}


