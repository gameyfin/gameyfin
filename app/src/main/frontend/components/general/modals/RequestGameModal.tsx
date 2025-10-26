import {
    addToast,
    Autocomplete,
    AutocompleteItem,
    Button,
    Input,
    Modal,
    ModalBody,
    ModalContent,
    Table,
    TableBody,
    TableCell,
    TableColumn,
    TableHeader,
    TableRow,
    Tooltip
} from "@heroui/react";
import React, {useEffect, useState} from "react";
import {ArrowRightIcon, MagnifyingGlassIcon} from "@phosphor-icons/react";
import {GameEndpoint, GameRequestEndpoint} from "Frontend/generated/endpoints";
import GameSearchResultDto from "Frontend/generated/org/gameyfin/app/games/dto/GameSearchResultDto";
import PluginIcon from "../plugin/PluginIcon";
import {useSnapshot} from "valtio/react";
import {pluginState} from "Frontend/state/PluginState";
import PluginDto from "Frontend/generated/org/gameyfin/app/core/plugins/dto/PluginDto";
import GameRequestCreationDto from "Frontend/generated/org/gameyfin/app/requests/dto/GameRequestCreationDto";
import Platform from "Frontend/generated/org/gameyfin/pluginapi/gamemetadata/Platform";
import {platformState} from "Frontend/state/PlatformState";

interface RequestGameModalProps {
    isOpen: boolean;
    onOpenChange: () => void;
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

    const plugins = useSnapshot(pluginState).state;
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

            addToast({
                title: "Request submitted",
                description: `Your request for "${game.title}" has been submitted.`,
                color: "success"
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
        <Modal isOpen={isOpen} onOpenChange={onOpenChange}
               hideCloseButton
               isDismissable={!isSearching && !isRequesting}
               isKeyboardDismissDisabled={!isSearching && !isRequesting}
               backdrop="opaque" size="5xl">
            <ModalContent>
                {(onClose) => (
                    <ModalBody className="my-4">
                        <div className="flex flex-col items-center">
                            <h2 className="text-xl font-semibold">Request a game</h2>
                        </div>
                        <Autocomplete
                            label="Platform"
                            size="sm"
                            allowsCustomValue={false}
                            selectedKey={selectedPlatform}
                            //@ts-ignore
                            onSelectionChange={(newSelection) => newSelection && setSelectedPlatform(newSelection)}
                        >
                            {Array.from(availablePlatforms).map((platform) => (
                                <AutocompleteItem key={platform}>{platform}</AutocompleteItem>
                            ))}
                        </Autocomplete>
                        <div className="flex flex-row gap-2 mb-4">
                            <Input value={searchTerm}
                                   onValueChange={setSearchTerm}
                                   onKeyDown={async (e) => {
                                       if (e.key === "Enter") {
                                           e.preventDefault();
                                           await search();
                                       }
                                   }}
                            />
                            <Button isIconOnly
                                    color="primary"
                                    onPress={search}
                                    isLoading={isSearching}>
                                <MagnifyingGlassIcon/>
                            </Button>
                        </div>

                        <div>
                            <Table removeWrapper isStriped isHeaderSticky
                                   classNames={{
                                       base: "h-80 overflow-y-auto",
                                   }}
                            >
                                <TableHeader>
                                    <TableColumn>Title & Release</TableColumn>
                                    <TableColumn>Developer(s)</TableColumn>
                                    <TableColumn>Publisher(s)</TableColumn>
                                    {/* width={1} keeps the column as far to the right as possible*/}
                                    <TableColumn>Sources</TableColumn>
                                    <TableColumn width={1}> </TableColumn>
                                </TableHeader>
                                <TableBody emptyContent="Your search did not match any games." items={searchResults}>
                                    {(item) => (
                                        <TableRow key={item.id}>
                                            <TableCell>
                                                {item.title} ({item.release ? new Date(item.release).getFullYear() : "unknown"})
                                            </TableCell>
                                            <TableCell>
                                                <div className="flex flex-col">
                                                    {item.developers ? item.developers.map(
                                                        developer => <p>{developer}</p>
                                                    ) : "unknown"}
                                                </div>
                                            </TableCell>
                                            <TableCell>
                                                <div className="flex flex-col">
                                                    {item.publishers ? item.publishers.map(
                                                        publisher => <p>{publisher}</p>
                                                    ) : "unknown"}
                                                </div>
                                            </TableCell>
                                            <TableCell>
                                                <div className="flex flex-row gap-2">
                                                    {Object.values(item.originalIds).map(
                                                        originalId => <PluginIcon
                                                            plugin={plugins[originalId.pluginId] as PluginDto}/>
                                                    )}
                                                </div>
                                            </TableCell>
                                            <TableCell>
                                                <Tooltip content="Pick this result">
                                                    <Button isIconOnly size="sm"
                                                            isDisabled={isRequesting !== null}
                                                            isLoading={isRequesting === item.id}
                                                            onPress={async () => {
                                                                setIsRequesting(item.id);
                                                                await requestGame(item);
                                                                setIsRequesting(null);
                                                                onClose();
                                                            }}>
                                                        <ArrowRightIcon/>
                                                    </Button>
                                                </Tooltip>
                                            </TableCell>
                                        </TableRow>
                                    )}
                                </TableBody>
                            </Table>
                        </div>
                    </ModalBody>
                )}
            </ModalContent>
        </Modal>
    );
}