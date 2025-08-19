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
        item && item.start <= today && today <= item.end
    );

    if (items.length === 0) return null;

    return (
        <aside className="newsbox" aria-label="Latest updates">
            <h2>News</h2>
            {items.map((news, idx) => (
                <article className="newsbox__item" key={`${news.title}-${idx}`}>
                    <header className="newsbox__header">
                        <h3 className="newsbox__title">{news.title}</h3>
                        {/*<span className="newsbox__dates">{n.start} – {n.end}</span>*/}
                    </header>
                    <div className="newsbox__body" dangerouslySetInnerHTML={{__html: news.text || ""}}>
                    </div>
                    {news.footer && (
                        <footer className="newsbox__footer">{news.footer}</footer>
                    )}
                </article>
            ))}
        </aside>
    );
}


