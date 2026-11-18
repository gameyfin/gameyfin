import React, {useEffect, useState} from "react";
import {Button, Chip, Input, Label, ListBox, Select, Spinner, TextField} from "@heroui/react";
import {GameStoreLinkEndpoint} from "Frontend/generated/endpoints";
import InfoHint from "Frontend/components/general/versions/InfoHint";
import IncludedDlcPicker from "Frontend/components/general/versions/IncludedDlcPicker";
import VersionOptionLabel, {versionMarkers} from "Frontend/components/general/versions/VersionOptionLabel";
import StoreSearchPicker from "Frontend/components/general/versions/StoreSearchPicker";
import {GameVersionEditorState} from "Frontend/components/general/versions/useGameVersionEditor";

const EDITION_PATTERNS = [
    /\b(?:Game of the Year|GOTY|Special|Anniversary|Definitive|Enhanced|Complete|Ultimate|Deluxe|Legendary|Gold|Premium|Platinum|Collector['’]?s)\s+Edition\b/i,
    /\bDirector['’]s Cut\b/i,
    /\bRemaster(?:ed)?\b/i,
];

/** Suggests an edition label from well-known edition terms in the store title, e.g. "Anniversary Edition". */
export function suggestEdition(storeTitle?: string): string {
    if (!storeTitle) return "";
    for (const pattern of EDITION_PATTERNS) {
        const match = storeTitle.match(pattern);
        if (match) return match[0];
    }
    return "";
}

function SectionHeading({title, hint}: { title: string; hint: string }) {
    return (
        <div className="flex items-center gap-1 pt-1">
            <span className="text-sm font-semibold">{title}</span>
            <InfoHint text={hint}/>
        </div>
    );
}

/** Edition names from the store database plus a keyword guess from the store title, as one-click suggestions. */
function EditionSuggestions({store, storeAppId, title, current, onPick}: {
    store: string;
    storeAppId: string;
    title?: string;
    current: string;
    onPick: (edition: string) => void;
}) {
    const [fromStore, setFromStore] = useState<string[]>([]);

    useEffect(() => {
        let cancelled = false;
        GameStoreLinkEndpoint.getEditionSuggestions(store, storeAppId)
            .then((names) => !cancelled && setFromStore(names ?? []))
            .catch(() => !cancelled && setFromStore([]));
        return () => {
            cancelled = true;
        };
    }, [store, storeAppId]);

    const guess = suggestEdition(title);
    const names = [...new Set([guess, ...fromStore].filter((name) => name && name !== current))].slice(0, 8);
    if (names.length === 0) return null;
    return (
        <div className="flex flex-wrap items-center gap-1">
            <span className="text-xs text-muted">Suggestions:</span>
            {names.map((name) => (
                <Button key={name} size="sm" variant="ghost" onPress={() => onPick(name)}>{name}</Button>
            ))}
        </div>
    );
}

interface GameVersionEditorProps {
    gameId: number;
    gameTitle: string;
    editor: GameVersionEditorState;
}

/** Edits are staged in [editor] and written when the metadata dialog is saved. */
export default function GameVersionEditor({gameId, gameTitle, editor}: GameVersionEditorProps) {
    const [adding, setAdding] = useState(false);
    const {links, stores, loading, pendingKey} = editor;
    const linkedKeys = new Set(links.map((link) => `${link.store}:${link.storeAppId}`));

    if (loading) return <div className="flex justify-center py-3"><Spinner size="sm"/></div>;

    return (
        <section className="flex flex-col gap-3">
            {links.length === 0 && !adding && (
                <p className="text-sm text-muted">No store is linked to this game yet.</p>
            )}

            {links.map((link) => {
                const busy = pendingKey === link.key;
                const isNew = link.linkId === undefined;
                return (
                    <div key={link.key} className="flex flex-col gap-2 rounded-xl bg-surface-secondary p-3">
                        <div className="flex flex-wrap items-center gap-2">
                            <span className="font-medium">{link.storeName}</span>
                            <span className="text-sm text-muted">
                                {link.title ? `${link.title} · ` : ""}ID {link.storeAppId}
                            </span>
                            {isNew && <Chip size="sm" variant="soft">New</Chip>}
                            {!link.storeAvailable && <Chip size="sm" variant="soft">Plugin disabled</Chip>}
                            {!isNew && links.length > 1 && link.tracked &&
                                <Chip size="sm" variant="soft">Primary</Chip>}
                        </div>

                        {!link.storeAvailable && (
                            <>
                                <p className="text-sm text-muted">
                                    The plugin for {link.storeName} is not enabled. Enable it to edit this store or to
                                    update its versions. The saved data is kept and shown as is.
                                </p>
                                <div>
                                    <Button size="sm" variant="ghost" isDisabled={busy}
                                            onPress={() => editor.removeLink(link.key)}>
                                        Remove
                                    </Button>
                                </div>
                            </>
                        )}

                        {link.storeAvailable && (<>
                        <SectionHeading
                            title="Edition"
                            hint="Leave empty for the standard edition. Fill it in only when this product is an edition sold as its own base product, e.g. Game of the Year Edition."/>
                        <TextField value={link.edition} onChange={(value) => editor.setEdition(link.key, value)}>
                            <Label>Edition</Label>
                            <Input
                                variant="secondary"
                                placeholder="e.g. Anniversary Edition (optional)"
                                maxLength={100}
                                onKeyDown={(event) => {
                                    if (event.key === "Enter") event.preventDefault();
                                }}
                            />
                        </TextField>
                        <EditionSuggestions store={link.store} storeAppId={link.storeAppId} title={link.title}
                                            current={link.edition}
                                            onPick={(name) => editor.setEdition(link.key, name)}/>

                        <SectionHeading
                            title="Product version"
                            hint="The version of the files in your library. Versions come from the product selected above, or from its base game when this edition has none of its own."/>
                        {link.loadingVersions ? (
                            <div className="flex items-center gap-2 text-sm text-muted">
                                <Spinner size="sm"/> Fetching versions…
                            </div>
                        ) : (
                            <Select
                                aria-label={`Library ${link.storeName} version`}
                                value={link.installedVersionId?.toString() ?? "none"}
                                isDisabled={busy || link.versions.length === 0}
                                onChange={(value) => {
                                    const selected = value?.toString() ?? "none";
                                    editor.setInstalledVersion(link.key, selected === "none" ? undefined : Number(selected));
                                }}
                            >
                                <Select.Trigger>
                                    <Select.Value/>
                                    <Select.Indicator/>
                                </Select.Trigger>
                                <Select.Popover>
                                    <ListBox>
                                        <ListBox.Item id="none" textValue="Not set">
                                            Library version: not set
                                            <ListBox.ItemIndicator/>
                                        </ListBox.Item>
                                        {link.versions.map((version, _, all) => {
                                            const markers = versionMarkers(all);
                                            return (
                                                <ListBox.Item key={version.id} id={version.id.toString()}
                                                              textValue={version.label}>
                                                    <VersionOptionLabel version={version} markers={markers}/>
                                                    <ListBox.ItemIndicator/>
                                                </ListBox.Item>
                                            );
                                        })}
                                    </ListBox>
                                </Select.Popover>
                            </Select>
                        )}

                        {!link.loadingVersions && link.edition.trim() !== "" && link.versions.length > 0 && (
                            <p className="text-xs text-muted">
                                Versions are those of the selected product. If this edition has no builds of its own,
                                they come from its base game.
                            </p>
                        )}

                        {!link.loadingVersions && link.versions.length === 0 && (
                            <p className="text-sm text-muted">
                                No versions are available yet. Use Refresh to try again.
                            </p>
                        )}

                        <div className="flex flex-wrap items-center gap-2">
                            <Button size="sm" variant="outline" isPending={busy} isDisabled={busy || link.loadingVersions}
                                    onPress={() => void editor.detect(link.key)}>
                                Detect
                            </Button>
                            <Button size="sm" variant="outline" isDisabled={busy || link.loadingVersions}
                                    onPress={() => isNew ? editor.refreshPreview(link.key) : void editor.refresh(link.key)}>
                                Refresh
                            </Button>
                            {!isNew && links.length > 1 && !link.tracked && (
                                <Button size="sm" variant="ghost" isDisabled={busy}
                                        onPress={() => editor.makePrimary(link.key)}>
                                    Make primary
                                </Button>
                            )}
                            <Button size="sm" variant="ghost" isDisabled={busy}
                                    onPress={() => editor.removeLink(link.key)}>
                                Remove
                            </Button>
                            {link.versionsCheckedAt && (
                                <span className="ml-auto text-xs text-muted">
                                    Checked {new Date(link.versionsCheckedAt).toLocaleString()}
                                </span>
                            )}
                        </div>

                        <SectionHeading
                            title="Extras"
                            hint="Everything that is not a separate base product: edition upgrades, DLC and bonus material such as soundtracks. Pick what the library files include."/>
                        <IncludedDlcPicker gameId={gameId} link={link}
                                           onChange={(dlc) => editor.setDlc(link.key, dlc)}/>
                        </>)}
                    </div>
                );
            })}

            {adding ? (
                <StoreSearchPicker
                    stores={stores}
                    initialQuery={gameTitle}
                    linkedKeys={linkedKeys}
                    onPick={(store, storeAppId, title) => {
                        editor.addLink(store, storeAppId, title, suggestEdition(title));
                        setAdding(false);
                    }}
                    onClose={() => setAdding(false)}
                />
            ) : links.length === 0 && (
                <div>
                    <Button size="sm" variant="outline" onPress={() => setAdding(true)}>Add store</Button>
                </div>
            )}
        </section>
    );
}
