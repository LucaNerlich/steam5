import React from "react";
import "@/styles/components/reviewRules.css";

export default function ReviewRules(): React.ReactElement {
    return (
        <div className="review-rules">
            <h3>How to play</h3>
            <ul>
                <li>Each day, guess the review-count bucket for each game pick.</li>
                <li>Tap one bucket to submit your guess. After submission, other choices are locked.</li>
                <li>Proceed through all rounds for the day, then share your results.</li>
            </ul>
            <h3>Buckets</h3>
            <ul>
                <li>
                    Bucket bounds are computed from all games’ total reviews using a log scale: we cap extreme outliers
                    (top ~0.5%),
                    take the minimum and capped-maximum, then split the log range into five equal steps and convert back
                    to whole numbers.
                    This creates wider upper buckets and narrower lower ones, avoiding one huge “whale” game skewing the
                    ranges.
                </li>
                <li>
                    We pick one game per bucket each day and then shuffle the order so you can’t infer the bucket by
                    position.
                    If a bucket can’t be filled (e.g., no eligible game), we fall back to “any” game that hasn’t been
                    picked recently.
                </li>
            </ul>
            <h3>Scoring</h3>
            <ul>
                <li>Points use a linear decay based on distance d between your guess and the actual bucket.</li>
                <li>Formula: points = max(0, 5 − 2 × d)</li>
                <li>Emoji: d=0 → 🟩, d=1 → 🟨, d=2 → 🟧, d≥3 → 🟥, invalid → ⬜</li>
                <li>Max points per round is 5; daily max = 5 × rounds.</li>
            </ul>
        </div>
    );
}


