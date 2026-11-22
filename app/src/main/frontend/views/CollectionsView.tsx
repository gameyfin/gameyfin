import {Button, useOverlayState} from "@heroui/react";
import {PlusIcon} from "@phosphor-icons/react";
import {useSnapshot} from "valtio/react";
import {useAuth} from "Frontend/util/auth";
import {collectionState} from "Frontend/state/CollectionState";
import {CollectionOverviewCard} from "Frontend/components/general/cards/CollectionOverviewCard";
import CollectionCreationModal from "Frontend/components/general/modals/CollectionCreationModal";

export default function CollectionsView() {
    const member = useAuth().state.user;
    const collections = useSnapshot(collectionState);
    const modal = useOverlayState();
    const mine = member ? collections.sorted.filter(item => item.ownerId === member.id) : [];
    const shared = collections.sorted.filter(item => !member || item.ownerId !== member.id);

    return <div className="flex flex-col gap-6 px-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
            <h1 className="text-2xl font-bold">Collections</h1>
            {member && collections.settings?.allowUserCollections &&
                <Button variant="primary" onPress={modal.open}><PlusIcon/>New collection</Button>}
        </div>
        {member && <section className="flex flex-col gap-3" aria-labelledby="your-collections">
            <h2 id="your-collections" className="text-lg font-semibold">Your collections</h2>
            <ul className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
                {mine.map(item => <CollectionOverviewCard key={item.id} collection={item}/>)}
            </ul>
        </section>}
        <section className="flex flex-col gap-3" aria-labelledby="shared-collections">
            <h2 id="shared-collections" className="text-lg font-semibold">Shared collections</h2>
            {shared.length ? <ul className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
                {shared.map(item => <CollectionOverviewCard key={item.id} collection={item}/>)}
            </ul> : <p className="text-muted">{collections.isLoaded ? "No shared collections." : "Loading collections…"}</p>}
        </section>
        {member && collections.settings?.allowUserCollections &&
            <CollectionCreationModal owned isOpen={modal.isOpen} onOpenChange={modal.setOpen}/>}
    </div>;
}
