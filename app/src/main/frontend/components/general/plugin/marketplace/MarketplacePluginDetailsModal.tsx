import React from "react";
import {Alert, Button, Chip, Link, Modal, Spinner, Tooltip} from "@heroui/react";
import Markdown from "react-markdown";
import remarkBreaks from "remark-breaks";
import moment from "moment-timezone";
import MarketplacePluginDto from "Frontend/generated/org/gameyfin/app/core/plugins/marketplace/dto/MarketplacePluginDto";
import MarketplacePluginReleaseDto
    from "Frontend/generated/org/gameyfin/app/core/plugins/marketplace/dto/MarketplacePluginReleaseDto";
import MarketplacePluginStatus
    from "Frontend/generated/org/gameyfin/app/core/plugins/marketplace/dto/MarketplacePluginStatus";
import MarketplacePluginLogo from "Frontend/components/general/plugin/marketplace/MarketplacePluginLogo";
import MarketplacePluginStatusChip from "Frontend/components/general/plugin/marketplace/MarketplacePluginStatusChip";
import {camelCaseToTitle} from "Frontend/util/utils";

interface MarketplacePluginDetailsModalProps {
    plugin: MarketplacePluginDto;
    isOpen: boolean;
    onOpenChange: (isOpen: boolean) => void;
    onInstall: (version?: string) => void;
    isPending: boolean;
}

function MarkdownText({children}: { children?: string }) {
    return (
        <Markdown
            remarkPlugins={[remarkBreaks]}
            components={{
                a(props) {
                    return <Link target="_blank" rel="noopener noreferrer" className="underline text-sm"
                                 href={props.href}>
                        {props.children}
                    </Link>
                }
            }}
        >{children}</Markdown>
    );
}

/** Index of the installed version in the (newest first) release list, -1 if unknown */
function installedReleaseIndex(plugin: MarketplacePluginDto): number {
    if (!plugin.installedVersion) return -1;
    return plugin.releases.findIndex(r => r.version === plugin.installedVersion);
}

function releaseActionLabel(plugin: MarketplacePluginDto, index: number): string {
    const installedIndex = installedReleaseIndex(plugin);
    if (!plugin.installedVersion) return "Install";
    if (installedIndex === -1) return "Install this version";
    return index < installedIndex ? "Update" : "Downgrade";
}

export default function MarketplacePluginDetailsModal({
                                                          plugin,
                                                          isOpen,
                                                          onOpenChange,
                                                          onInstall,
                                                          isPending
                                                      }: MarketplacePluginDetailsModalProps) {
    const canInstallLatest = plugin.status === MarketplacePluginStatus.NOT_INSTALLED
        || plugin.status === MarketplacePluginStatus.UPDATE_AVAILABLE;

    function releaseAction(release: MarketplacePluginReleaseDto, index: number) {
        if (release.version === plugin.installedVersion) {
            return <Chip size="sm" variant="soft" color="success">Installed</Chip>;
        }

        if (!release.compatible) {
            return (
                <Tooltip delay={0}>
                    <Tooltip.Trigger>
                        <Chip size="sm" variant="soft" color="danger">Incompatible</Chip>
                    </Tooltip.Trigger>
                    <Tooltip.Content placement="bottom" className="bg-foreground text-background">
                        {`Requires Gameyfin ${release.requires ?? "(unknown)"}`}
                    </Tooltip.Content>
                </Tooltip>
            );
        }

        return (
            <Button size="sm" variant="secondary" isDisabled={isPending}
                    onPress={() => onInstall(release.version)}>
                {releaseActionLabel(plugin, index)}
            </Button>
        );
    }

    return (
        <Modal>
            <Modal.Backdrop isOpen={isOpen} onOpenChange={onOpenChange}>
                <Modal.Container size="lg" scroll="inside">
                    <Modal.Dialog>
                        {({close}) => (
                            <>
                                <Modal.Header>
                                    <Modal.Heading>{plugin.name}</Modal.Heading>
                                </Modal.Header>
                                <Modal.Body className="flex flex-col gap-4 text-sm">
                                    {!plugin.officialMarketplace &&
                                        <Alert status="warning">
                                            <Alert.Indicator/>
                                            <Alert.Content>
                                                <Alert.Title className="font-bold">Third-party plugin</Alert.Title>
                                                <Alert.Description>
                                                    This plugin is provided by the marketplace
                                                    "{plugin.marketplaceName}", which is not maintained by the
                                                    Gameyfin team. Only install plugins from sources you trust.
                                                </Alert.Description>
                                            </Alert.Content>
                                        </Alert>
                                    }

                                    <div className="flex flex-row items-center gap-8">
                                        <MarketplacePluginLogo plugin={plugin}/>
                                        <table className="text-left table-auto">
                                            <tbody>
                                            {Object.entries({
                                                "ID": <code>{plugin.id}</code>,
                                                "Author(s)": plugin.author,
                                                "Latest version": plugin.latestVersion,
                                                "License": plugin.license,
                                                "Marketplace": plugin.marketplaceName,
                                                "Types": plugin.types.length > 0
                                                    ? plugin.types.map(camelCaseToTitle).join(", ")
                                                    : undefined,
                                                "Status": <MarketplacePluginStatusChip plugin={plugin}/>,
                                                "URL": plugin.url &&
                                                    <Link target="_blank" rel="noopener noreferrer"
                                                          className="text-sm underline" href={plugin.url}>
                                                        {plugin.url}
                                                    </Link>,
                                            }).map(([key, value]) => {
                                                if (!value) return null;
                                                return (
                                                    <tr key={key}>
                                                        <td className="text-muted w-0 min-w-28">{key}</td>
                                                        <td className="flex flex-row gap-1">{value}</td>
                                                    </tr>
                                                );
                                            })}
                                            </tbody>
                                        </table>
                                    </div>

                                    {plugin.tags.length > 0 &&
                                        <div className="flex flex-row flex-wrap gap-1">
                                            {plugin.tags.map(tag => <Chip key={tag} size="sm">{tag}</Chip>)}
                                        </div>
                                    }

                                    <div>
                                        <b>Description</b>
                                        <MarkdownText>{plugin.description ?? plugin.shortDescription}</MarkdownText>
                                    </div>

                                    <div className="flex flex-col gap-2">
                                        <b>Releases</b>
                                        {plugin.releases.length === 0 &&
                                            <p className="text-muted">No releases available.</p>}
                                        {plugin.releases.map((release, index) =>
                                            <div key={release.version}
                                                 className="flex flex-col gap-1 rounded-xl bg-surface-secondary p-3">
                                                <div className="flex flex-row items-center justify-between gap-2">
                                                    <div className="flex flex-col">
                                                        <span className="font-semibold">v{release.version}</span>
                                                        <span className="text-xs text-muted">
                                                            {release.date && moment(release.date).format("LL")}
                                                            {release.date && release.requires && " · "}
                                                            {release.requires && `Requires Gameyfin ${release.requires}`}
                                                        </span>
                                                    </div>
                                                    {releaseAction(release, index)}
                                                </div>
                                                {release.changelog &&
                                                    <div className="text-xs">
                                                        <MarkdownText>{release.changelog}</MarkdownText>
                                                    </div>
                                                }
                                            </div>
                                        )}
                                    </div>
                                </Modal.Body>
                                <Modal.Footer>
                                    <Button variant="ghost" onPress={close}>Close</Button>
                                    {canInstallLatest &&
                                        <Button variant="primary" isPending={isPending} onPress={() => onInstall()}>
                                            {isPending && <Spinner size="sm" color="current"/>}
                                            {plugin.status === MarketplacePluginStatus.UPDATE_AVAILABLE
                                                ? `Update to v${plugin.latestCompatibleVersion}`
                                                : `Install v${plugin.latestCompatibleVersion}`}
                                        </Button>
                                    }
                                </Modal.Footer>
                            </>
                        )}
                    </Modal.Dialog>
                </Modal.Container>
            </Modal.Backdrop>
        </Modal>
    );
}
