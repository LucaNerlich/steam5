import React from "react";
import news from "@/data/news.json";
import "@/styles/components/newsBox.css";

type NewsEntry = {
    start: string; // yyyy-mm-dd
    end: string;   // yyyy-mm-dd
    title: string;
    text: string;
    footer?: string;
};

function todayIso(): string {
    // simple yyyy-mm-dd in UTC
    return new Date().toISOString().slice(0, 10);
}

export default function NewsBox(): React.ReactElement | null {
    const today = todayIso();
    const items = (news as NewsEntry[]).filter(item =>
        item && typeof item.start === "string" && typeof item.end === "string" &&
        item.start <= today && today <= item.end
    );

    if (items.length === 0) return null;

    return (
        <aside className="newsbox" aria-label="Latest updates">
            <h2>News</h2>
            {items.map((n, idx) => (
                <article className="newsbox__item" key={`${n.title}-${idx}`}>
                    <header className="newsbox__header">
                        <h3 className="newsbox__title">{n.title}</h3>
                        {/*<span className="newsbox__dates">{n.start} – {n.end}</span>*/}
                    </header>
                    <div className="newsbox__body">
                        <p>{n.text}</p>
                    </div>
                    {n.footer && (
                        <footer className="newsbox__footer">{n.footer}</footer>
                    )}
                </article>
            ))}
        </aside>
    );
}


