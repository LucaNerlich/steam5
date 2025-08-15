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


