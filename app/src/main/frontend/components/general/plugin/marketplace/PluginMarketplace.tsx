import React, {useEffect, useMemo, useState} from "react";
import {Alert, Button, ListBox, SearchField, Select, Spinner, toast, Tooltip, useOverlayState} from "@heroui/react";
import {ArrowsClockwiseIcon, StorefrontIcon} from "@phosphor-icons/react";
import {useSnapshot} from "valtio/react";
import MarketplacePluginDto
    from "Frontend/generated/org/gameyfin/app/core/plugins/marketplace/dto/MarketplacePluginDto";
import MarketplacePluginStatus
    from "Frontend/generated/org/gameyfin/app/core/plugins/marketplace/dto/MarketplacePluginStatus";
import {initializeMarketplaceState, marketplaceState} from "Frontend/state/MarketplaceState";
import MarketplacePluginCard from "Frontend/components/general/plugin/marketplace/MarketplacePluginCard";
import PluginMarketplacesModal from "Frontend/components/general/plugin/marketplace/PluginMarketplacesModal";
import {statusLabels} from "Frontend/components/general/plugin/marketplace/MarketplacePluginStatusChip";
import {camelCaseToTitle} from "Frontend/util/utils";

const ALL = "all";

function matchesSearch(plugin: MarketplacePluginDto, term: string): boolean {
    if (!term) return true;
    return [plugin.name, plugin.id, plugin.author, plugin.shortDescription, plugin.description, ...plugin.tags]
        .some(value => value?.toLowerCase().includes(term));
}

export default function PluginMarketplace() {
    const state = useSnapshot(marketplaceState);
    const marketplacesModal = useOverlayState();
    const [searchTerm, setSearchTerm] = useState("");
    const [typeFilter, setTypeFilter] = useState<string>(ALL);
    const [statusFilter, setStatusFilter] = useState<string>(ALL);
    const [loadError, setLoadError] = useState<string>();

    async function load(force: boolean) {
        setLoadError(undefined);
        try {
            await initializeMarketplaceState(force);
        } catch (e: any) {
            setLoadError(e?.message ?? "Failed to load marketplace");
            if (force) toast.danger("Failed to refresh marketplace", {description: e?.message});
        }
    }

    useEffect(() => {
        void load(false);
    }, []);

    const plugins = state.plugins as MarketplacePluginDto[];

    const availableTypes = useMemo(
        () => Array.from(new Set(plugins.flatMap(p => p.types))).sort(),
        [plugins]
    );

    const filteredPlugins = useMemo(() => {
        const term = searchTerm.trim().toLowerCase();
        return plugins
            .filter(p => matchesSearch(p, term))
            .filter(p => typeFilter === ALL || p.types.includes(typeFilter))
            .filter(p => statusFilter === ALL || p.status === statusFilter)
            .sort((a, b) =>
                Number(b.officialMarketplace) - Number(a.officialMarketplace) || a.name.localeCompare(b.name)
            );
    }, [plugins, searchTerm, typeFilter, statusFilter]);

    const failedMarketplaces = state.marketplaces.filter(m => m.enabled && m.lastError);

    return (
        <div className="flex flex-col gap-4">
            <div className="flex flex-col md:flex-row gap-2 md:items-end">
                <SearchField aria-label="Search marketplace"
                             name="search"
                             className="grow"
                             value={searchTerm}
                             onChange={setSearchTerm}>
                    <SearchField.Group>
                        <SearchField.SearchIcon/>
                        <SearchField.Input placeholder="Search plugins..."/>
                        <SearchField.ClearButton/>
                    </SearchField.Group>
                </SearchField>
                <FilterSelect label="Type"
                              value={typeFilter}
                              onChange={setTypeFilter}
                              options={availableTypes.map(t => ({id: t, label: camelCaseToTitle(t)}))}
                              className="md:w-72"/>
                <FilterSelect label="Status"
                              value={statusFilter}
                              onChange={setStatusFilter}
                              options={Object.values(MarketplacePluginStatus).map(s => ({
                                  id: s,
                                  label: statusLabels[s]
                              }))}
                              className="md:w-48"/>
                <div className="flex flex-row gap-2">
                    <Tooltip delay={0}>
                        <Tooltip.Trigger>
                            <Button isIconOnly variant="secondary" aria-label="Refresh marketplaces"
                                    isDisabled={state.isLoading}
                                    onPress={() => load(true)}>
                                {state.isLoading ? <Spinner size="sm" color="current"/> : <ArrowsClockwiseIcon/>}
                            </Button>
                        </Tooltip.Trigger>
                        <Tooltip.Content placement="bottom">Refresh</Tooltip.Content>
                    </Tooltip>
                    <Button variant="secondary" onPress={marketplacesModal.open}>
                        <StorefrontIcon/>
                        Marketplaces
                    </Button>
                </div>
            </div>

            {loadError &&
                <Alert status="danger">
                    <Alert.Indicator/>
                    <Alert.Content>
                        <Alert.Title className="font-bold">Could not load the marketplace</Alert.Title>
                        <Alert.Description>{loadError}</Alert.Description>
                    </Alert.Content>
                </Alert>
            }

            {failedMarketplaces.length > 0 &&
                <Alert status="warning">
                    <Alert.Indicator/>
                    <Alert.Content>
                        <Alert.Title className="font-bold">Some marketplaces could not be loaded</Alert.Title>
                        <Alert.Description>
                            <ul>
                                {failedMarketplaces.map(m => <li key={m.id}><b>{m.name}</b>: {m.lastError}</li>)}
                            </ul>
                        </Alert.Description>
                    </Alert.Content>
                </Alert>
            }

            {!state.isLoaded && state.isLoading &&
                <div className="flex flex-row justify-center p-8"><Spinner/></div>
            }

            {state.isLoaded && filteredPlugins.length === 0 &&
                <div className="flex flex-row justify-center p-8">
                    <p className="text-gray-500">
                        {plugins.length === 0 ? "No plugins available." : "No plugins match your filters."}
                    </p>
                </div>
            }

            {filteredPlugins.length > 0 &&
                <div className="grid grid-cols-300px gap-4">
                    {filteredPlugins.map(plugin =>
                        <MarketplacePluginCard key={`${plugin.marketplaceId}/${plugin.id}`} plugin={plugin}/>
                    )}
                </div>
            }

            <PluginMarketplacesModal isOpen={marketplacesModal.isOpen} onOpenChange={marketplacesModal.setOpen}/>
        </div>
    );
}

interface FilterSelectProps {
    label: string;
    value: string;
    onChange: (value: string) => void;
    options: { id: string, label: string }[];
    className: string;
}

function FilterSelect({label, value, onChange, options, className}: FilterSelectProps) {
    return (
        <Select aria-label={`Filter by ${label.toLowerCase()}`}
                className={className}
                value={value}
                onChange={(key) => onChange(key?.toString() ?? ALL)}>
            <Select.Trigger>
                <span className="text-muted text-sm mr-1">{label}:</span>
                <Select.Value/>
                <Select.Indicator/>
            </Select.Trigger>
            <Select.Popover>
                <ListBox>
                    <ListBox.Item id={ALL} textValue="All">
                        All
                        <ListBox.ItemIndicator/>
                    </ListBox.Item>
                    {options.map(option =>
                        <ListBox.Item key={option.id} id={option.id} textValue={option.label}>
                            {option.label}
                            <ListBox.ItemIndicator/>
                        </ListBox.Item>
                    )}
                </ListBox>
            </Select.Popover>
        </Select>
    );
}
