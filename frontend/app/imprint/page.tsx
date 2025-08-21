import type {Metadata} from "next";

export const metadata: Metadata = {
    title: "Imprint",
    description: "Legal imprint for Steam5.org",
    alternates: {canonical: "/imprint"},
};

export default function ImprintPage() {
    return (
        <section className="container">
            <h1>Imprint</h1>
            <p>Steam5.org</p>
            <p>
                <strong>Owner</strong>: Luca Nerlich
            </p>
            <p>
                Website: <a href="https://lucanerlich.com" target="_blank" rel="noopener noreferrer">lucanerlich.com</a>
            </p>
        </section>
    );
}


