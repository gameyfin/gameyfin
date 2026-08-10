import GameDto from "Frontend/generated/org/gameyfin/app/games/dto/GameDto";
import {useMemo} from "react";
import {ProgressCircle} from "@heroui/react";
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
        <ProgressCircle
            color={color}
            value={completeness}
            size="sm"
        >
            <ProgressCircle.Track>
                <ProgressCircle.TrackCircle/>
                <ProgressCircle.FillCircle/>
            </ProgressCircle.Track>
        </ProgressCircle>
        <p>{completeness}% </p>
    </div>;
}