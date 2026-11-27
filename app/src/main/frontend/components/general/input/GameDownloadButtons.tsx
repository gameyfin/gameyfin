import {type ReactNode, useEffect, useState} from "react";
import {Button, Tooltip, useOverlayState} from "@heroui/react";
import {DownloadSimpleIcon, FolderOpenIcon} from "@phosphor-icons/react";
import BrowseContentsModal from "Frontend/components/general/modals/BrowseContentsModal";
import ComboButton from "Frontend/components/general/input/ComboButton";
import {downloadGame} from "Frontend/endpoints/DownloadEndpoint";
import {DownloadProviderEndpoint} from "Frontend/generated/endpoints";
import type DownloadProviderDto from "Frontend/generated/org/gameyfin/app/core/download/provider/DownloadProviderDto";
import type GameMetadataDto from "Frontend/generated/org/gameyfin/app/games/dto/GameMetadataDto";
import {humanFileSize} from "Frontend/util/utils";

export default function GameDownloadButtons({gameId, metadata, leadingAction, compact}: {
    gameId: number;
    metadata: GameMetadataDto;
    leadingAction?: ReactNode;
    compact?: boolean;
}) {
    const [providers, setProviders] = useState<DownloadProviderDto[]>([]);
    const browse = useOverlayState();
    useEffect(() => {
        let active = true;
        DownloadProviderEndpoint.getProviders().then(result => {
            if (active) setProviders(result);
        });
        return () => {
            active = false;
        };
    }, [gameId]);

    if (!providers.length && !leadingAction) return null;
    const options = Object.fromEntries(providers.map(provider => [provider.key, {
        label: provider.name,
        description: provider.shortDescription ?? provider.description,
        action: () => downloadGame(gameId, provider.key)
    }]));
    const capable = providers.filter(provider => provider.supportsFileSelection);
    const canBrowse = metadata.directory && capable.length > 0;

    return <div className="flex w-full min-w-0 items-stretch gap-2 sm:w-auto">
        {leadingAction}
        {canBrowse && <Tooltip>
            <Button isIconOnly aria-label="Browse contents" className="size-12 shrink-0" onPress={browse.open}>
                <FolderOpenIcon className="size-6"/>
            </Button>
            <Tooltip.Content>Browse contents</Tooltip.Content>
        </Tooltip>}
        {providers.length > 0 &&
            <ComboButton className="h-12 min-w-0 grow sm:grow-0" icon={<DownloadSimpleIcon size={24}/>}
                         description={humanFileSize(metadata.fileSize)} options={options}
                         preferredOptionKey="preferred-download-method" compact={compact}
                         actionLabel={compact ? "Download" : undefined}/>}
        {canBrowse && browse.isOpen &&
            <BrowseContentsModal gameId={gameId} providers={capable} onClose={browse.close}/>}
    </div>;
}
