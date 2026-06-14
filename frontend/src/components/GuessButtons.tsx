"use client";

import React, {useCallback, useRef} from "react";
import {useFormStatus} from "react-dom";
import {useRouter} from "next/navigation";
import "@/styles/components/reviewGuessButtons.css";

type GuessButtonsProps = {
    appId: number;
    buckets: string[];
    bucketTitles?: string[];
    selectedLabel: string | null;
    onSelect: (label: string) => void;
    submitted: boolean;
    isPending: boolean;
    formAction: (formData: FormData) => void;
    helperText?: string;
    // Route to warm up when the player hovers/focuses a guess button, so the next
    // round is already cached by the time they submit and click "Next round".
    prefetchHref?: string | null;
};

function BucketButton({label, title, selectedLabel, onSelect, submitted, isPending, onPrefetch}: {
    label: string;
    title?: string;
    selectedLabel: string | null;
    onSelect: (label: string) => void;
    submitted: boolean;
    isPending: boolean;
    onPrefetch?: () => void;
}) {
    const {pending} = useFormStatus();
    const isSelected = selectedLabel === label;
    const disabled = (pending || submitted || isPending);
    const showLoading = isPending && isSelected;
    return (
        <button
            name="bucketGuess"
            value={label}
            disabled={disabled}
            type="submit"
            className={`review-round__button${isSelected ? ' is-selected' : ''}`}
            onClick={() => onSelect(label)}
            onMouseEnter={onPrefetch}
            onFocus={onPrefetch}
            title={title}
            aria-busy={showLoading}
        >
            {showLoading ? "Submitting..." : label}
        </button>
    );
}

export default function GuessButtons(props: Readonly<GuessButtonsProps>): React.ReactElement {
    const {appId, buckets, bucketTitles, selectedLabel, onSelect, submitted, isPending, formAction, helperText, prefetchHref} = props;
    const router = useRouter();
    const prefetchedRef = useRef(false);
    const handlePrefetch = useCallback(() => {
        if (!prefetchHref || prefetchedRef.current) return;
        prefetchedRef.current = true; // prefetch once — the RSC payload is then cached
        router.prefetch(prefetchHref);
    }, [router, prefetchHref]);

    const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        const submitter = (event.nativeEvent as SubmitEvent).submitter;
        if (submitter instanceof HTMLButtonElement) {
            formAction(new FormData(event.currentTarget, submitter));
            return;
        }
        formAction(new FormData(event.currentTarget));
    };

    return (
        <div className="review-round__guess-body">
            {helperText ? <p className="review-round__guess-helper">{helperText}</p> : null}
            <form className="review-round__buttons" onSubmit={handleSubmit} aria-busy={isPending}>
                <input type="hidden" name="appId" value={appId}/>
                {buckets.map((label, i) => (
                    <BucketButton key={label}
                                  label={label}
                                  title={(bucketTitles && bucketTitles[i] ? bucketTitles[i] : undefined)}
                                  selectedLabel={selectedLabel}
                                  onSelect={onSelect}
                                  submitted={submitted}
                                  isPending={isPending}
                                  onPrefetch={handlePrefetch}
                    />
                ))}
            </form>
        </div>
    );
}


