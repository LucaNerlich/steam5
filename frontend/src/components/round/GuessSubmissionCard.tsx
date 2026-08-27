"use client";

import GuessButtons from "@/components/GuessButtons";
import OtherPlayersNow from "@/components/OtherPlayersNow";
import {buildSteamLoginUrl} from "@/lib/steamLogin";

type GuessSubmissionCardProps = {
    appId: number;
    buckets: string[];
    bucketTitles?: string[];
    selectedLabel: string | null;
    onSelect: (label: string) => void;
    submitted: boolean;
    isPending: boolean;
    formAction: (formData: FormData) => void;
    /** True when the session was lost mid-round (result did not count). */
    signedOutDuringPlay: boolean;
    /** Submission error from the last action state, if any. */
    error?: string;
};

/**
 * The "Submit Your Guess" card: bucket buttons plus the session-lost notice
 * and submission error line.
 */
export default function GuessSubmissionCard({
                                                appId,
                                                buckets,
                                                bucketTitles,
                                                selectedLabel,
                                                onSelect,
                                                submitted,
                                                isPending,
                                                formAction,
                                                signedOutDuringPlay,
                                                error
                                            }: GuessSubmissionCardProps) {
    return (
        <section className="review-round__guess-card" aria-labelledby="guess-submission">
            <div className="review-round__guess-header">
                <h2 id="guess-submission">Submit Your Guess</h2>
                <OtherPlayersNow/>
            </div>
            <GuessButtons
                appId={appId}
                buckets={buckets}
                bucketTitles={bucketTitles}
                selectedLabel={selectedLabel}
                onSelect={onSelect}
                submitted={submitted}
                isPending={isPending}
                formAction={formAction}
                helperText="Pick the review bucket that best matches this game."
            />
            {signedOutDuringPlay ? (
                <p className="text-muted review-round__error">
                    You&apos;ve been signed out, so this result wasn&apos;t saved.{" "}
                    <button type="button" className="btn-link" onClick={() => {
                        window.location.href = buildSteamLoginUrl();
                    }}>Sign in with Steam</button>
                    &nbsp;to save your results.
                </p>
            ) : error && (
                <p className="text-muted review-round__error">Error: {error}</p>
            )}
        </section>
    );
}
