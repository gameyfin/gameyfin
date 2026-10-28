import React, {useState} from "react";
import {toast, useOverlayState} from "@heroui/react";
import MarketplacePluginDto from "Frontend/generated/org/gameyfin/app/core/plugins/marketplace/dto/MarketplacePluginDto";
import {installPlugin} from "Frontend/state/MarketplaceState";
import ConfirmModal from "Frontend/components/general/modals/ConfirmModal";

/**
 * Encapsulates the installation of a marketplace plugin including the confirmation dialog
 * for plugins from third-party marketplaces and the resulting notifications.
 */
export function useMarketplaceInstall(plugin: MarketplacePluginDto) {
    const confirmModal = useOverlayState();
    const [pendingVersion, setPendingVersion] = useState<string | undefined>();
    const [isInstalling, setIsInstalling] = useState(false);

    async function doInstall(version: string | undefined) {
        const isUpdate = plugin.installedVersion != null;
        setIsInstalling(true);
        try {
            await installPlugin(plugin.marketplaceId, plugin.id, version);
            confirmModal.close();
            toast.info(`${plugin.name} ${isUpdate ? "update" : "installation"} started`, {
                description: "Progress is shown in the active tasks popover."
            });
        } catch (e: any) {
            toast.danger(`Failed to install ${plugin.name}`, {description: e?.message});
        } finally {
            setIsInstalling(false);
        }
    }

    /** Installs the given version (or the latest compatible one), asking for confirmation if required */
    function install(version?: string) {
        if (plugin.officialMarketplace) {
            void doInstall(version);
        } else {
            setPendingVersion(version);
            confirmModal.open();
        }
    }

    const confirmationModal = (
        <ConfirmModal isOpen={confirmModal.isOpen}
                      onOpenChange={confirmModal.setOpen}
                      title={`Install ${plugin.name}?`}
                      confirmLabel="Install anyway"
                      confirmVariant="primary"
                      isPending={isInstalling}
                      onConfirm={() => doInstall(pendingVersion)}>
            <p>
                <b>{plugin.name}</b> (version {pendingVersion ?? plugin.latestCompatibleVersion}) is provided by
                the third-party marketplace <b>{plugin.marketplaceName}</b>.
            </p>
            <p>
                Plugins run with the same permissions as Gameyfin itself and have full access to your data.
                Only install plugins from sources you trust.
            </p>
            <p className="text-muted">
                Plugins that are not signed by the Gameyfin team will be installed in a disabled state.
            </p>
        </ConfirmModal>
    );

    return {install, isInstalling, confirmationModal};
}
