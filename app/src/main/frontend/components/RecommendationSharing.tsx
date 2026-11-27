import {Switch} from "@heroui/react";
import {useId} from "react";

interface RecommendationSharingProps {
    enabled: boolean | undefined;
    isSubmitting: boolean;
    loadFailed: boolean;
    onChange: (enabled: boolean) => void;
}

export default function RecommendationSharing({
                                                  enabled,
                                                  isSubmitting,
                                                  loadFailed,
                                                  onChange
                                              }: RecommendationSharingProps) {
    const descriptionId = useId();

    return <div className="flex flex-col gap-2">
        <Switch isSelected={enabled === true} isDisabled={enabled === undefined || isSubmitting}
                aria-labelledby={`${descriptionId}-label`} aria-describedby={descriptionId}
                onChange={onChange}>
            <Switch.Content>
                <Switch.Control><Switch.Thumb/></Switch.Control>
                <span id={`${descriptionId}-label`}>Share game activity for recommendations</span>
            </Switch.Content>
        </Switch>
        <p id={descriptionId} className="text-sm text-muted">
            Allow Gameyfin to use your ratings, play status and favourites in recommendations for other members.
            <br/>You can turn sharing off at any time.
        </p>
        {loadFailed && <p className="text-sm text-danger" role="alert">Could not load your sharing preference.</p>}
    </div>;
}
