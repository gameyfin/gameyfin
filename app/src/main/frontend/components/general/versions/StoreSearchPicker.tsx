import React, {useCallback, useEffect, useRef, useState} from "react";
import {Button, Input, Label, ListBox, Select, Spinner, TextField} from "@heroui/react";
import {GameStoreSearchEndpoint} from "Frontend/generated/endpoints";
import GameStoreDto from "Frontend/generated/org/gameyfin/app/games/versions/GameStoreDto";

/** Plugin patterns are Java regexes; JS has no inline flags, so a leading (?i) becomes the "i" flag. */
function toRegExp(pattern: string): RegExp {
    return pattern.startsWith("(?i)") ? new RegExp(pattern.slice(4), "i") : new RegExp(pattern);
}

/** Mirrors the server-side parsing, which validates again when the link is saved. */
function parseStoreId(store: GameStoreDto, input: string): string | undefined {
    const trimmed = input.trim();
    const candidate = store.urlPatterns
        .map((url) => toRegExp(url).exec(trimmed)?.[1])
        .find(Boolean) ?? trimmed;
    const normalized = store.caseInsensitiveIds ? candidate.toLowerCase() : candidate;
    return toRegExp(store.productIdPattern).test(normalized) ? normalized : undefined;
}

type SearchResult = Awaited<ReturnType<typeof GameStoreSearchEndpoint.search>>[number];

interface StoreSearchPickerProps {
    /** Stores of the active plugins, at least one */
    stores: GameStoreDto[];
    initialQuery: string;
    /** `${store.id}:${id}` of products that are already linked */
    linkedKeys: Set<string>;
    onPick: (store: string, storeAppId: string, title?: string) => void;
    onClose: () => void;
}

export default function StoreSearchPicker({stores, initialQuery, linkedKeys, onPick, onClose}: StoreSearchPickerProps) {
    const [storeId, setStoreId] = useState(stores[0].id);
    const store = stores.find((candidate) => candidate.id === storeId) ?? stores[0];
    const [query, setQuery] = useState(initialQuery);
    const [results, setResults] = useState<SearchResult[]>();
    const [searching, setSearching] = useState(false);
    const [error, setError] = useState<string>();
    const [manual, setManual] = useState(false);
    const [manualInput, setManualInput] = useState("");
    const latestRequest = useRef(0);

    const search = useCallback(async (searchStore: GameStoreDto, term: string) => {
        const request = ++latestRequest.current;
        setSearching(true);
        setError(undefined);
        try {
            const found = await GameStoreSearchEndpoint.search(searchStore.id, term);
            if (request === latestRequest.current) setResults(found);
        } catch (e) {
            if (request === latestRequest.current) {
                setResults(undefined);
                setError(`Could not search ${searchStore.displayName}. You can enter the ID manually instead.`);
            }
        } finally {
            if (request === latestRequest.current) setSearching(false);
        }
    }, []);

    // Searching with the game's title right away is what makes picking a product a one-click action
    useEffect(() => {
        if (store.supportsSearch) {
            void search(store, initialQuery);
        } else {
            latestRequest.current++;
            setResults(undefined);
            setError(undefined);
            setSearching(false);
        }
    }, [store, initialQuery, search]);

    const showManual = manual || !store.supportsSearch;

    const manualId = parseStoreId(store, manualInput);

    function addManual() {
        if (!manualId) return;
        onPick(store.id, manualId);
        setManualInput("");
    }

    return (
        <div className="flex flex-col gap-3 rounded-xl border border-default-200 p-3">
            <div className="flex flex-wrap items-end gap-2">
                <Select
                    aria-label="Store"
                    className="w-40"
                    value={store.id}
                    onChange={(value) => setStoreId(String(value))}
                >
                    <Select.Trigger>
                        <Select.Value/>
                        <Select.Indicator/>
                    </Select.Trigger>
                    <Select.Popover>
                        <ListBox>
                            {stores.map((option) => (
                                <ListBox.Item key={option.id} id={option.id} textValue={option.displayName}>
                                    {option.displayName}
                                    <ListBox.ItemIndicator/>
                                </ListBox.Item>
                            ))}
                        </ListBox>
                    </Select.Popover>
                </Select>
                {store.supportsSearch && <>
                <TextField className="min-w-48 flex-1" value={query} onChange={setQuery}>
                    <Label className="sr-only">Search term</Label>
                    <Input
                        variant="secondary"
                        placeholder="Search by game title"
                        onKeyDown={(e) => {
                            if (e.key === "Enter") {
                                e.preventDefault();
                                void search(store, query);
                            }
                        }}
                    />
                </TextField>
                <Button size="sm" variant="outline" isDisabled={searching || query.trim().length < 2}
                        onPress={() => void search(store, query)}>
                    Search
                </Button>
                </>}
                <Button size="sm" variant="ghost" onPress={onClose}>Done</Button>
            </div>

            {!store.supportsSearch &&
                <p className="text-sm text-muted">
                    No enabled {store.displayName} plugin supports searching. Enter a product ID or URL below, or enable
                    a {store.displayName} plugin that can search.
                </p>}
            {searching && <div className="flex justify-center py-2"><Spinner size="sm"/></div>}
            {error && <p className="text-sm text-danger">{error}</p>}
            {!searching && results?.length === 0 &&
                <p className="text-sm text-muted">No results on {store.displayName}. Try another search term.</p>}
            {!searching && results && results.length > 0 && (
                <ul className="flex max-h-56 flex-col gap-1 overflow-y-auto">
                    {results.map((result) => {
                        const linked = linkedKeys.has(`${store.id}:${result.id}`);
                        return (
                            <li key={result.id} className="flex items-center gap-3 rounded-lg p-1 hover:bg-default-100">
                                {result.imageUrl
                                    ? <img src={result.imageUrl} alt=""
                                           referrerPolicy="no-referrer"
                                           className="h-12 w-9 rounded object-cover"
                                           onError={(event) => {
                                               event.currentTarget.style.visibility = "hidden";
                                           }}/>
                                    : <div className="h-12 w-9 rounded bg-default-200"/>}
                                <div className="flex min-w-0 flex-1 flex-col">
                                    <span className="truncate">{result.title}</span>
                                    <span className="truncate text-xs text-muted">
                                        {[result.subtitle, `ID ${result.id}`].filter(Boolean).join(" · ")}
                                    </span>
                                </div>
                                <Button size="sm" variant="outline" isDisabled={linked}
                                        onPress={() => onPick(store.id, result.id, result.title)}>
                                    {linked ? "Added" : "Add"}
                                </Button>
                            </li>
                        );
                    })}
                </ul>
            )}

            <div className="border-t border-default-200 pt-2">
                {showManual ? (
                    <div className="flex flex-wrap items-end gap-2">
                        <TextField className="min-w-48 flex-1" value={manualInput} onChange={setManualInput}>
                            <Label className="sr-only">Product ID or URL</Label>
                            <Input
                                variant="secondary"
                                placeholder={`${store.displayName} product ID or URL`}
                                onKeyDown={(e) => {
                                    if (e.key === "Enter") {
                                        e.preventDefault();
                                        addManual();
                                    }
                                }}
                            />
                        </TextField>
                        <Button size="sm" isDisabled={!manualId || linkedKeys.has(`${store.id}:${manualId}`)}
                                onPress={addManual}>
                            Add
                        </Button>
                        {manualInput.trim() && !manualId &&
                            <p className="w-full text-xs text-danger">Not a valid {store.displayName} ID or URL</p>}
                    </div>
                ) : (
                    <Button size="sm" variant="ghost" onPress={() => setManual(true)}>
                        Can't find it? Enter an ID or URL manually
                    </Button>
                )}
            </div>
        </div>
    );
}
