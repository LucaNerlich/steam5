"use client";

import React from "react";
import Form from "next/form";
import {useFormStatus} from "react-dom";
import "@/styles/components/reviewGuessButtons.css";

type GuessButtonsProps = {
    appId: number;
    buckets: string[];
    bucketTitles?: string[];
    selectedLabel: string | null;
    onSelect: (label: string) => void;
    submitted: boolean;
    formAction: (formData: FormData) => void;
};

function BucketButton({label, title, selectedLabel, onSelect, submitted}: {
    label: string;
    title?: string;
    selectedLabel: string | null;
    onSelect: (label: string) => void;
    submitted: boolean
}) {
    const {pending} = useFormStatus();
    const isSelected = selectedLabel === label;
    const disabled = (pending || submitted);
    return (
        <button
            name="bucketGuess"
            value={label}
            disabled={disabled}
            type="submit"
            className={`review-round__button${isSelected ? ' is-selected' : ''}`}
            onClick={() => onSelect(label)}
            title={title}
        >
            {label}
        </button>
    );
}

export default function GuessButtons(props: Readonly<GuessButtonsProps>): React.ReactElement {
    const {appId, buckets, bucketTitles, selectedLabel, onSelect, submitted, formAction} = props;
    return (
        <Form action={formAction} className="review-round__buttons">
            <input type="hidden" name="appId" value={appId}/>
            {buckets.map((label, i) => (
                <BucketButton key={label}
                              label={label}
                              title={(bucketTitles && bucketTitles[i] ? bucketTitles[i] : undefined)}
                              selectedLabel={selectedLabel}
                              onSelect={onSelect}
                              submitted={submitted}
                />
            ))}
        </Form>
    );
}


