import type {Metadata} from "next";

export const metadata: Metadata = {
    title: "Privacy Policy",
    description: "GDPR-compliant privacy policy for Steam5.org",
    alternates: {canonical: "/privacy"},
    robots: { index: false, follow: true },
    openGraph: {
        title: 'Privacy Policy',
        description: 'GDPR-compliant privacy policy for Steam5.org',
        url: '/privacy',
        images: ['/opengraph-image'],
    },
};

export default function PrivacyPage() {
    return (
        <section className="container">
            <h1>Privacy Policy</h1>
            <p>Last updated: {new Date().toISOString().slice(0, 10)}</p>

            <h2>Introduction</h2>
            <p>
                We are committed to protecting your personal data and respecting your privacy. This website is operated
                in full
                compliance with the General Data Protection Regulation (GDPR).
            </p>

            <h2>Data Controller</h2>
            <p>The data controller is: Luca Nerlich (Steam5.org)</p>

            <h2>What data we collect</h2>
            <ul>
                <li>Account-related data when you log in with Steam (your SteamID and basic profile info as provided by
                    Steam).
                </li>
                <li>Gameplay interactions (e.g., guesses and scores) for the purpose of providing the service.</li>
                <li>Anonymous analytics via Umami (no cookies, no personal data).</li>
            </ul>

            <h2>Analytics</h2>
            <p>
                We use Umami Analytics to understand high-level usage. Umami does not use cookies and does not collect
                personal
                data. For details, see <a href="https://umami.is/docs/faq" target="_blank" rel="noopener noreferrer">Umami’s
                documentation</a>.
            </p>

            <h2>Legal bases</h2>
            <ul>
                <li>Performance of a contract (Art. 6(1)(b) GDPR) for providing the game and related features.</li>
                <li>Legitimate interests (Art. 6(1)(f) GDPR) for ensuring security and preventing abuse.</li>
            </ul>

            <h2>Data retention</h2>
            <p>We retain data only as long as necessary to provide the service or as required by law.</p>

            <h2>Your rights</h2>
            <ul>
                <li>Right of access, rectification, erasure, restriction, and data portability.</li>
                <li>Right to object to processing based on legitimate interests.</li>
                <li>Right to lodge a complaint with a supervisory authority.</li>
            </ul>

            <h2>Data sharing</h2>
            <p>We do not sell personal data. Data may be shared with service providers strictly necessary to operate the
                site.</p>

            <h2>International transfers</h2>
            <p>If data is transferred outside the EU/EEA, we ensure appropriate safeguards (e.g., Standard Contractual
                Clauses).</p>

            <h2>Security</h2>
            <p>We use industry-standard security measures and HTTPS everywhere to protect your data.</p>

            <h2>Contact</h2>
            <p>For privacy inquiries, contact: luca.nerlich at gmail.com</p>
        </section>
    );
}


