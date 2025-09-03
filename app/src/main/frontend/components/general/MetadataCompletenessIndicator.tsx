import GameDto from "Frontend/generated/org/gameyfin/app/games/dto/GameDto";
import {useMemo} from "react";
import {CircularProgress} from "@heroui/react";
import {metadataCompleteness} from "Frontend/util/utils";

interface MetadataCompletenessIndicatorProps {
    game: GameDto;
}

export default function MetadataCompletenessIndicator({game}: MetadataCompletenessIndicatorProps) {
    const completeness = useMemo(() => metadataCompleteness(game), [game]);

    const color = useMemo(() => {
        return completeness > 80 ? "success" : completeness > 50 ? "warning" : "danger";
    }, [completeness]);

    return <div className="flex flex-row items-center gap-1">
        <CircularProgress
            color={color}
            value={completeness}
            disableAnimation
            size="sm"
            strokeWidth={5}
        />
        <p>{completeness}% </p>
    </div>;
}