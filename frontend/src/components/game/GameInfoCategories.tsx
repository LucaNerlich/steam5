import React from "react";
import type {Category} from "@/types/review-game";

/**
 * Renders the game's category pills, one per unique category.
 */
export default function GameInfoCategories({categories}: {
    categories: Category[];
}): React.ReactElement {
    return (
        <div className="game-info__section">
            <h3>Categories</h3>
            <ul className="game-info__pills" aria-label="Categories">
                {categories.map((c) => (
                    <li key={`cat-${c.id ?? c.description?.toLowerCase().trim()}`} className="pill">
                        {c.description}
                    </li>
                ))}
            </ul>
        </div>
    );
}
