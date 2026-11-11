import GameSearchResults from "./GameSearchResults";
import ResponsiveDialog from "Frontend/components/layout/ResponsiveDialog";
import {Button, ComboBox, Input, ListBox, Modal, toast, Tooltip, Spinner} from "@heroui/react";
import React, {useEffect, useState} from "react";
import {ArrowRightIcon, MagnifyingGlassIcon} from "@phosphor-icons/react";
import {GameEndpoint, GameRequestEndpoint} from "Frontend/generated/endpoints";
import GameSearchResultDto from "Frontend/generated/org/gameyfin/app/games/dto/GameSearchResultDto";
import {useSnapshot} from "valtio/react";
import GameRequestCreationDto from "Frontend/generated/org/gameyfin/app/requests/dto/GameRequestCreationDto";
import Platform from "Frontend/generated/org/gameyfin/pluginapi/gamemetadata/Platform";
import {platformState} from "Frontend/state/PlatformState";

interface RequestGameModalProps {
    isOpen: boolean;
    onOpenChange: (isOpen: boolean) => void;
}

// TODO: Maybe make this configurable in the admin settings?
const DEFAULT_PLATFORM_FOR_NEW_REQUESTS = "PC (Microsoft Windows)";

export default function RequestGameModal({
                                             isOpen,
                                             onOpenChange
                                         }: RequestGameModalProps) {
    const [selectedPlatform, setSelectedPlatform] = useState<string>(DEFAULT_PLATFORM_FOR_NEW_REQUESTS);
    const [searchTerm, setSearchTerm] = useState("");
    const [searchResults, setSearchResults] = useState<GameSearchResultDto[]>([]);
    const [isSearching, setIsSearching] = useState(false);
    const [isRequesting, setIsRequesting] = useState<string | null>(null);

    const availablePlatforms = useSnapshot(platformState).available;

    useEffect(() => {
        setSearchTerm("");
        setSearchResults([]);
    }, [isOpen]);

    async function requestGame(game: GameSearchResultDto) {
        const request: GameRequestCreationDto = {
            title: game.title,
            release: game.release,
            // Since we can only request for one platform at a time, just pick the first one
            platform: game.platforms ? game.platforms[0] : DEFAULT_PLATFORM_FOR_NEW_REQUESTS as Platform
        }

        try {
            await GameRequestEndpoint.create(request);

            toast.success("Request submitted", {
                description: `Your request for "${game.title}" has been submitted.`
            });
        } catch (e) {
            setIsSearching(false);
            setIsRequesting(null);
        }
    }

    async function search() {
        setIsSearching(true);
        const results = await GameEndpoint.getPotentialMatches(searchTerm, [selectedPlatform] as Platform[]);
        setSearchResults(results);
        setIsSearching(false);
    }

    return (
        <Modal>
            <Modal.Backdrop isOpen={isOpen} onOpenChange={onOpenChange}
                            isDismissable={!isSearching && !isRequesting}>
                <ResponsiveDialog size="lg" className="max-w-5xl">
                    <Modal.Dialog>
                        {({close}) => (
                            <Modal.Body className="my-4">
                                <div className="flex flex-col items-center">
                                    <h2 className="text-xl font-semibold">Request a game</h2>
                                </div>
                                <ComboBox
                                    aria-label="Platform"
                                    allowsCustomValue={false}
                                    value={selectedPlatform}
                                    onChange={(newSelection) => newSelection && setSelectedPlatform(newSelection as string)}
                                >
                                    <ComboBox.InputGroup>
                                        <Input placeholder="Platform"/>
                                        <ComboBox.Trigger/>
                                    </ComboBox.InputGroup>
                                    <ComboBox.Popover>
                                        <ListBox>
                                            {Array.from(availablePlatforms).map((platform) => (
                                                <ListBox.Item key={platform} id={platform} textValue={platform}>
                                                    {platform}
                                                    <ListBox.ItemIndicator/>
                                                </ListBox.Item>
                                            ))}
                                        </ListBox>
                                    </ComboBox.Popover>
                                </ComboBox>
                                <div className="flex flex-row gap-2 my-4">
                                    <Input aria-label="Search games" value={searchTerm}
                                           className="flex-1"
                                           onChange={(e) => setSearchTerm(e.target.value)}
                                           onKeyDown={async (e) => {
                                               if (e.key === "Enter") {
                                                   e.preventDefault();
                                                   await search();
                                               }
                                           }}
                                    />
                                    <Button isIconOnly aria-label="Search games"
                                            variant="primary"
                                            onPress={search}
                                            isPending={isSearching}>
                                        {isSearching ? <Spinner size="sm" color="current"/> : <MagnifyingGlassIcon/>}
                                    </Button>
                                </div>

                                <div>
                                    <GameSearchResults results={searchResults} actions={item => (
                                        <Tooltip>
                                            <Button isIconOnly size="sm" aria-label={`Pick ${item.title}`}
                                                    isDisabled={isRequesting !== null}
                                                    isPending={isRequesting === item.id}
                                                    onPress={async () => {
                                                        setIsRequesting(item.id);
                                                        await requestGame(item);
                                                        setIsRequesting(null);
                                                        close();
                                                    }}>
                                                {isRequesting === item.id ? <Spinner size="sm" color="current"/> : <ArrowRightIcon/>}
                                            </Button>
                                            <Tooltip.Content placement="bottom">Pick this result</Tooltip.Content>
                                        </Tooltip>
                                    )}/>
                                </div>
                            </Modal.Body>
                        )}
                    </Modal.Dialog>
                </ResponsiveDialog>
            </Modal.Backdrop>
        </Modal>
    );
}