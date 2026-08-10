import {
    Button,
    ComboBox,
    Input,
    ListBox,
    Modal,
    Table,
    toast,
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
                <Modal.Container size="lg" className="max-w-5xl">
                    <Modal.Dialog>
                        {({close}) => (
                            <Modal.Body className="my-4">
                                <div className="flex flex-col items-center">
                                    <h2 className="text-xl font-semibold">Request a game</h2>
                                </div>
                                <ComboBox
                                    aria-label="Platform"
                                    allowsCustomValue={false}
                                    selectedKey={selectedPlatform}
                                    onSelectionChange={(newSelection) => newSelection && setSelectedPlatform(newSelection as string)}
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
                                <div className="flex flex-row gap-2 mb-4">
                                    <Input value={searchTerm}
                                           onChange={(e) => setSearchTerm(e.target.value)}
                                           onKeyDown={async (e) => {
                                               if (e.key === "Enter") {
                                                   e.preventDefault();
                                                   await search();
                                               }
                                           }}
                                    />
                                    <Button isIconOnly
                                            variant="primary"
                                            onPress={search}
                                            isPending={isSearching}>
                                        <MagnifyingGlassIcon/>
                                    </Button>
                                </div>

                                <div>
                                    <Table className="h-80">
                                        <Table.ScrollContainer className="h-80">
                                            <Table.Content>
                                                <Table.Header>
                                                    <Table.Column id="title">Title & Release</Table.Column>
                                                    <Table.Column id="developers">Developer(s)</Table.Column>
                                                    <Table.Column id="publishers">Publisher(s)</Table.Column>
                                                    <Table.Column id="sources">Sources</Table.Column>
                                                    <Table.Column id="actions"> </Table.Column>
                                                </Table.Header>
                                                <Table.Body renderEmptyState={() => <p className="text-center text-muted p-4">Your search did not match any games.</p>}
                                                            items={searchResults}>
                                                    {(item) => (
                                                        <Table.Row key={item.id}>
                                                            <Table.Cell>
                                                                {item.title} ({item.release ? new Date(item.release).getFullYear() : "unknown"})
                                                            </Table.Cell>
                                                            <Table.Cell>
                                                                <div className="flex flex-col">
                                                                    {item.developers ? item.developers.map(
                                                                        developer => <p>{developer}</p>
                                                                    ) : "unknown"}
                                                                </div>
                                                            </Table.Cell>
                                                            <Table.Cell>
                                                                <div className="flex flex-col">
                                                                    {item.publishers ? item.publishers.map(
                                                                        publisher => <p>{publisher}</p>
                                                                    ) : "unknown"}
                                                                </div>
                                                            </Table.Cell>
                                                            <Table.Cell>
                                                                <div className="flex flex-row gap-2">
                                                                    {Object.values(item.originalIds).map(
                                                                        originalId => <PluginIcon
                                                                            plugin={plugins[originalId.pluginId] as PluginDto}/>
                                                                    )}
                                                                </div>
                                                            </Table.Cell>
                                                            <Table.Cell>
                                                                <Tooltip>
                                                                    <Tooltip.Trigger>
                                                                        <Button isIconOnly size="sm"
                                                                                isDisabled={isRequesting !== null}
                                                                                isPending={isRequesting === item.id}
                                                                                onPress={async () => {
                                                                                    setIsRequesting(item.id);
                                                                                    await requestGame(item);
                                                                                    setIsRequesting(null);
                                                                                    close();
                                                                                }}>
                                                                            <ArrowRightIcon/>
                                                                        </Button>
                                                                    </Tooltip.Trigger>
                                                                    <Tooltip.Content placement="bottom">Pick this
                                                                        result</Tooltip.Content>
                                                                </Tooltip>
                                                            </Table.Cell>
                                                        </Table.Row>
                                                    )}
                                                </Table.Body>
                                            </Table.Content>
                                        </Table.ScrollContainer>
                                    </Table>
                                </div>
                            </Modal.Body>
                        )}
                    </Modal.Dialog>
                </Modal.Container>
            </Modal.Backdrop>
        </Modal>
    );
}