import {ImageResponse} from "next/og";

export const runtime = "edge";
export const size = {width: 1200, height: 630};
export const contentType = "image/png";

export default function Image() {
    const bg = "#2563eb"; // matches logo background
    const circleWhite = "#ffffff";
    const circleAmber = "#fbbf24";

    return new ImageResponse(
        (
            // Inline styles are required in OG image generation; no external CSS is supported here
            <div
                style={{
                    width: "100%",
                    height: "100%",
                    display: "flex",
                    flexDirection: "column",
                    alignItems: "stretch",
                    justifyContent: "space-between",
                    background: bg,
                }}
            >
                <div style={{position: "relative", width: "100%", height: 260, display: "flex"}}>
                    {/* Logo motif scaled up */}
                    <div style={{position: "absolute", left: 120, top: 70, width: 140, height: 140, background: circleWhite, borderRadius: 9999}}/>
                    <div style={{position: "absolute", left: 280, top: 50, width: 220, height: 220, background: circleAmber, borderRadius: 9999}}/>
                </div>
                <div style={{padding: "0 96px 72px 96px", color: "#fff", display: "flex", flexDirection: "column"}}>
                    <div style={{fontSize: 84, fontWeight: 800, lineHeight: 1}}>Steam5</div>
                    <div style={{fontSize: 40, opacity: 0.9, marginTop: 8}}>Review Game</div>
                    <div style={{fontSize: 28, opacity: 0.9, marginTop: 18}}>steam5.org</div>
                </div>
            </div>
        ),
        {width: size.width, height: size.height}
    );
}


