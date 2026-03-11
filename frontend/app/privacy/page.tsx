import type {Metadata} from "next";

export const metadata: Metadata = {
    title: "Privacy Policy",
    description: "GDPR-compliant privacy policy explaining data protection and your rights at Steam5.org.",
    alternates: {canonical: "/privacy"},
    robots: { index: false, follow: true },
    openGraph: {
        title: 'Privacy Policy',
        description: 'GDPR-compliant privacy policy explaining data protection and your rights at Steam5.org.',
        url: '/privacy',
        images: ['/opengraph-image'],
    },
};

export default function PrivacyPage() {
    return (
        <section className="container">
            <h1>Privacy Policy</h1>
            <p>Last updated: 2026-03-11</p>

            <h2>Introduction</h2>
            <p>
                We are committed to protecting your personal data and respecting your privacy. This website is operated
                in compliance with the General Data Protection Regulation (GDPR) and applicable German data protection law.
            </p>

            <h2>Data Controller</h2>
            <p>The data controller is: Luca Nerlich (Steam5.org)</p>

            <h2>What data we collect</h2>
            <ul>
                <li>Account-related data when you log in with Steam (your SteamID and basic profile info as provided by
                    Steam).
                </li>
                <li>Gameplay interactions (e.g., guesses and scores) for the purpose of providing the service.</li>
                <li>Basic technical request data processed by our servers (for example IP address in server logs) for security and operations.</li>
                <li>Privacy-friendly analytics via our self-hosted Umami instance.</li>
            </ul>

            <h2>Analytics</h2>
            <p>
                We use a self-hosted Umami Analytics instance to understand high-level usage. Umami is configured in a
                privacy-friendly way and does not use marketing cookies. Analytics data is hosted by us.
                For details, see <a href="https://umami.is/docs/faq" target="_blank" rel="noopener noreferrer">Umami documentation</a>.
            </p>

            <h2>Hosting and infrastructure</h2>
            <p>
                Steam5 is self-hosted on infrastructure in Germany (Hetzner), managed via Coolify on Ubuntu servers.
            </p>

            <h2>External data sources (Steam/Valve)</h2>
            <p>
                Game metadata and image, video and media assets are loaded from Steam/Valve services. When your browser requests this
                content, technical data (such as your IP address, user agent, and request metadata) is transmitted to
                Valve. Please review Valve's legal information and policies:
                {" "}
                <a href="https://store.steampowered.com/legal/" target="_blank" rel="noopener noreferrer">
                    https://store.steampowered.com/legal/
                </a>.
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
            <p>
                We do not sell personal data. Data may be shared with service providers strictly necessary to operate the
                site (for example infrastructure hosting). Steam/Valve receives data directly when third-party Steam
                resources are loaded by your browser.
            </p>

            <h2>International transfers</h2>
            <p>
                Our own primary hosting is in Germany. However, when Steam/Valve resources are loaded, data processing may
                occur outside the EU/EEA under Valve's responsibility. Where we initiate international transfers ourselves,
                we use appropriate safeguards (for example Standard Contractual Clauses) where required.
            </p>

            <h2>Security</h2>
            <p>We use industry-standard security measures and HTTPS everywhere to protect your data.</p>

            <h2>Contact</h2>
            <p>For privacy inquiries, contact: luca.nerlich at gmail.com</p>
        </section>
    );
}


