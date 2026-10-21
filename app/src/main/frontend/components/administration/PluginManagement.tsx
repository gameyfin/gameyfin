import React, {useMemo, useState} from "react";
import {PluginManagementSection} from "Frontend/components/general/plugin/PluginManagementSection";
import {pluginState} from "Frontend/state/PluginState";
import {useSnapshot} from "valtio/react";
import {SearchField, Tabs} from "@heroui/react";
import {useLocation, useNavigate} from "react-router";
import PluginMarketplace from "Frontend/components/general/plugin/marketplace/PluginMarketplace";
import {marketplaceState} from "Frontend/state/MarketplaceState";

const TAB_INSTALLED = "#installed";
const TAB_MARKETPLACE = "#marketplace";

export default function PluginManagement() {

    // Defined manually for now to control the layout (order of categories)
    const pluginTypes = ["GameMetadataProvider", "DownloadProvider"];

    const state = useSnapshot(pluginState);
    const marketplace = useSnapshot(marketplaceState);
    const {hash} = useLocation();
    const navigate = useNavigate();
    const [searchTerm, setSearchTerm] = useState("");

    const selectedTab = hash === TAB_MARKETPLACE ? TAB_MARKETPLACE : TAB_INSTALLED;
    const updateCount = useMemo(() => Object.keys(marketplace.updates).length, [marketplace.updates]);

    return state.isLoaded && (
        <div className="flex flex-col">
            <div className="flex flex-row grow justify-between mb-4">
                <h2 className="text-2xl font-bold">Plugins</h2>
            </div>

            <Tabs
                variant="primary"
                selectedKey={selectedTab}
                onSelectionChange={(newKey) => navigate(newKey.toString(), {replace: true})}
                className="w-full"
            >
                <Tabs.ListContainer className="w-full">
                    <Tabs.List aria-label="Plugin management sections" className="w-full">
                        <Tabs.Tab id={TAB_INSTALLED} className="flex-1 justify-center">
                            Installed
                            <Tabs.Indicator/>
                        </Tabs.Tab>
                        <Tabs.Tab id={TAB_MARKETPLACE} className="flex-1 justify-center gap-2">
                            Marketplace
                            {updateCount > 0 &&
                                <span className="rounded-full bg-accent text-accent-foreground text-xs px-2"
                                      aria-label={`${updateCount} updates available`}>
                                    {updateCount}
                                </span>
                            }
                            <Tabs.Indicator/>
                        </Tabs.Tab>
                    </Tabs.List>
                </Tabs.ListContainer>
                <Tabs.Panel id={TAB_INSTALLED}>
                    <div className="flex flex-col gap-8 mt-4">
                        <SearchField aria-label="Search installed plugins"
                                     name="search"
                                     value={searchTerm}
                                     onChange={setSearchTerm}>
                            <SearchField.Group>
                                <SearchField.SearchIcon/>
                                <SearchField.Input placeholder="Search installed plugins..."/>
                                <SearchField.ClearButton/>
                            </SearchField.Group>
                        </SearchField>

                        {pluginTypes.map(type =>
                            <PluginManagementSection key={type} type={type} searchTerm={searchTerm}/>
                        )}
                    </div>
                </Tabs.Panel>
                <Tabs.Panel id={TAB_MARKETPLACE}>
                    <div className="mt-4">
                        <PluginMarketplace/>
                    </div>
                </Tabs.Panel>
            </Tabs>
        </div>
    );
}
