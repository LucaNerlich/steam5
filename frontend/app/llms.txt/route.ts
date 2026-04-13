export function GET() {
    const base = (process.env.NEXT_PUBLIC_DOMAIN || 'https://steam5.org').replace(/\/$/, '');

    const body = `# Steam5

> Daily Steam review guessing game — guess review counts for five Steam games each day, climb leaderboards, and share your results.

Steam5 presents five randomly selected Steam games per day. Players guess each game's total review count bucket, earning points for accuracy and speed. The site features daily, weekly, and seasonal leaderboards, player profiles with performance statistics, and an archive of past rounds.

The project is open source (AGPL) and built with Next.js 15 (frontend) and Spring Boot 3.5 (backend).

## Main pages

- [Play today's round](${base}/review-guesser): The daily guessing game
- [Leaderboard](${base}/review-guesser/leaderboard): All-time leaderboard
- [Today's leaderboard](${base}/review-guesser/leaderboard/today): Daily rankings
- [Weekly leaderboard](${base}/review-guesser/leaderboard/weekly): Weekly rankings
- [Season leaderboard](${base}/review-guesser/leaderboard/season): Current season rankings
- [Archive](${base}/review-guesser/archive): Browse past daily rounds

## Legal

- [Privacy policy](${base}/privacy): GDPR-compliant privacy policy
- [Imprint](${base}/imprint): Legal ownership and contact

## Optional

- [Source code](https://github.com/LucaNerlich/steam5): GitHub repository (AGPL-3.0)
`;

    return new Response(body, {
        headers: {
            'Content-Type': 'text/plain; charset=utf-8',
        },
    });
}
