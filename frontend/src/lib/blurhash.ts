"use client";

import {decode} from "blurhash";
import {useEffect, useState} from "react";

export function blurhashToDataURL(hash: string, width = 32, height = 20): string | null {
    try {
        if (!hash) return null;
        const pixels = decode(hash, width, height); // Uint8ClampedArray RGBA
        const canvas = document.createElement('canvas');
        canvas.width = width;
        canvas.height = height;
        const ctx = canvas.getContext('2d');
        if (!ctx) return null;
        const imageData = ctx.createImageData(width, height);
        imageData.data.set(pixels);
        ctx.putImageData(imageData, 0, 0);
        return canvas.toDataURL('image/png');
    } catch {
        return null;
    }
}

export function useBlurhashDataURL(hash?: string | null, width = 32, height = 20): string | undefined {
    const [url, setUrl] = useState<string | undefined>(undefined);
    useEffect(() => {
        if (!hash) {
            setUrl(undefined);
            return;
        }
        const data = blurhashToDataURL(hash, width, height);
        setUrl(data ?? undefined);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [hash, width, height]);
    return url;
}


