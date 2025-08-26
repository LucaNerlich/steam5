import {ImageResponse} from "next/og";

export const runtime = "edge";
export const size = {width: 1200, height: 630};
export const contentType = "image/png";

function arrayBufferToBase64(buffer: ArrayBuffer): string {
    let binary = "";
    const bytes = new Uint8Array(buffer);
    const len = bytes.byteLength;
    for (let i = 0; i < len; i++) {
        binary += String.fromCharCode(bytes[i]);
    }
    // Edge runtime provides btoa
    return btoa(binary);
}

export default async function Image() {
    const bg = "#0b1220"; // dark brand background

    // Load the SVG from the public directory and embed via data URL for reliability in Edge runtime
    const iconArrayBuffer = await fetch(new URL("../public/icon-full.svg", import.meta.url)).then(r => r.arrayBuffer());
    const iconDataUri = `data:image/svg+xml;base64,${arrayBufferToBase64(iconArrayBuffer)}`;

    return new ImageResponse(
        (
            <div
                style={{
                    width: "100%",
                    height: "100%",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    background: bg,
                    padding: 40,
                }}
            >
                <div
                    style={{
                        width: 1000,
                        height: 500,
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                        background: "#0f172a",
                        border: "2px solid #1f2937",
                        borderRadius: 24,
                    }}
                >
                    <img src={iconDataUri} alt="Steam5" width={680} height={240} />
                </div>
            </div>
        ),
        {width: size.width, height: size.height}
    );
}


