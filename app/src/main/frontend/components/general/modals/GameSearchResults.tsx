import {ReactNode} from "react";
import {Table} from "@heroui/react";
import {useSnapshot} from "valtio/react";
import CompactRecordList from "Frontend/components/layout/CompactRecordList";
import TableScroller from "Frontend/components/layout/TableScroller";
import {COMPACT_QUERY, useMediaQuery} from "Frontend/util/useMediaQuery";
import GameSearchResultDto from "Frontend/generated/org/gameyfin/app/games/dto/GameSearchResultDto";
import {pluginState} from "Frontend/state/PluginState";
import PluginIcon from "../plugin/PluginIcon";

/** Search and mutation state stay in the requesting dialog across layout changes. */
export default function GameSearchResults({results, actions}: {
    results: GameSearchResultDto[];
    actions: (result: GameSearchResultDto) => ReactNode;
}) {
    const compact = useMediaQuery(COMPACT_QUERY);
    const plugins = useSnapshot(pluginState).state;
    const empty = <p className="p-4 text-center text-muted">Your search did not match any games.</p>;

    function fields(item: GameSearchResultDto) {
        return [
            {
                label: "Title & Release",
                content: <div className="min-w-0">
                    <p className="font-medium wrap-anywhere">{item.title}</p>
                    <p className="text-xs text-muted mt-1">{item.release ? new Date(item.release).getFullYear() : "Release unknown"}</p>
                </div>
            },
            {
                label: "Developer(s)",
                content: <div className="flex flex-col">{item.developers?.map(developer => <p
                    key={developer}>{developer}</p>) ?? "unknown"}</div>
            },
            {
                label: "Publisher(s)",
                content: <div className="flex flex-col">{item.publishers?.map(publisher => <p
                    key={publisher}>{publisher}</p>) ?? "unknown"}</div>
            },
            {
                label: "Sources", content: <div className="flex w-max flex-nowrap items-center gap-2 whitespace-nowrap">
                    {[...new Set(Object.values(item.originalIds).map(id => id.pluginId))].map(pluginId =>
                        <span key={pluginId} className="inline-flex shrink-0">
                            {plugins[pluginId] ? <PluginIcon plugin={plugins[pluginId]} size={20}/>
                                : <span>{pluginId}</span>}
                        </span>)}
                </div>
            },
            {label: "Actions", content: actions(item)}
        ];
    }

    return compact
        ? <CompactRecordList label="Search results" items={results} itemKey={item => item.id} fields={fields}
                             empty={empty}/>
        : <Table variant="secondary" className="h-80">
            <TableScroller label="Search results" className="h-80">
                <Table.Content aria-label="Search results" className="table-auto">
                    <Table.Header className="sticky top-0 z-10 bg-background">
                        <Table.Column id="title" isRowHeader className="w-[34%]">Title & Release</Table.Column>
                        <Table.Column id="developers">Developer(s)</Table.Column>
                        <Table.Column id="publishers">Publisher(s)</Table.Column>
                        <Table.Column id="sources" className="w-px whitespace-nowrap">Sources</Table.Column>
                        <Table.Column id="actions" className="w-px whitespace-nowrap">Actions</Table.Column>
                    </Table.Header>
                    <Table.Body items={results} renderEmptyState={() => empty}>
                        {item => <Table.Row key={item.id}>
                            {fields(item).map(field => <Table.Cell key={field.label} className="align-middle">{field.content}</Table.Cell>)}
                        </Table.Row>}
                    </Table.Body>
                </Table.Content>
            </TableScroller>
        </Table>;
}
