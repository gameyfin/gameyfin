import GameDto from "Frontend/generated/org/gameyfin/app/games/dto/GameDto";
import {useMemo} from "react";
import {CircularProgress, Tooltip} from "@heroui/react";
import {GameMetadataAdminDto} from "Frontend/dtos/GameDtos";

interface MetadataCompletenessIndicatorProps {
    game: GameDto;
    showPercentageOnHover?: boolean;
}

export default function MetadataCompletenessIndicator({
                                                          game,
                                                          showPercentageOnHover
                                                      }: MetadataCompletenessIndicatorProps) {
    const completeness = (game.metadata as GameMetadataAdminDto).completenessScore;

    const color = useMemo(() => {
        return completeness > 80 ? "success" : completeness > 50 ? "warning" : "danger";
    }, [completeness]);

    const circularProgress = <CircularProgress
        color={color}
        value={completeness}
        disableAnimation
        size="sm"
        strokeWidth={5}
    />

    if (showPercentageOnHover) {
        return <Tooltip content={`Metadata completeness: ${completeness}%`}>
            {circularProgress}
        </Tooltip>;
    } else {
        return <div className="flex flex-row items-center gap-1">
            {circularProgress}
            <p>{completeness}% </p>
        </div>;
    }
}