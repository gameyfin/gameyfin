import {type ComponentProps, useEffect, useLayoutEffect, useRef, useState} from "react";
import {Button, Dropdown, Popover, Skeleton, Tabs, toast, ToggleButton, Tooltip} from "@heroui/react";
import {
    ArchiveIcon,
    ArrowSquareOutIcon,
    BellIcon,
    CaretDoubleDownIcon,
    CheckIcon,
    ChecksIcon,
    CheckSquareIcon,
    FunnelIcon,
    ListChecksIcon,
    PlayIcon,
    PowerIcon,
    ShieldCheckIcon,
    TrashIcon,
    TrashSimpleIcon
} from "@phosphor-icons/react";
import {useSnapshot} from "valtio/react";
import {NotificationEndpoint} from "Frontend/generated/endpoints";
import NotificationSeverity from "Frontend/generated/org/gameyfin/pluginapi/notifications/NotificationSeverity";
import type NotificationDto from "Frontend/generated/org/gameyfin/app/core/notifications/NotificationDto";
import type NotificationActionDto from "Frontend/generated/org/gameyfin/app/core/notifications/NotificationActionDto";
import MarkdownContent from "Frontend/components/general/MarkdownContent";
import ConfirmModal from "Frontend/components/general/modals/ConfirmModal";
import RestartConfirmationModal from "Frontend/components/general/modals/RestartConfirmationModal";
import {
    filterNotificationSeverity,
    loadMoreNotifications,
    notificationState,
    refreshNotifications,
    showNotificationArchive
} from "Frontend/state/NotificationState";

const severityClasses: Record<string, string> = {
    INFO: "text-blue-500",
    SUCCESS: "text-success",
    WARNING: "text-warning",
    ERROR: "text-danger"
};

function NotificationButton({label, ...props}: ComponentProps<typeof Button> & { label: string }) {
    return <Tooltip>
        <Button isIconOnly variant="ghost" aria-label={label} {...props}/>
        <Tooltip.Content>{label}</Tooltip.Content>
    </Tooltip>;
}

export default function NotificationPopover() {
    const state = useSnapshot(notificationState);
    const [selectMode, setSelectMode] = useState(false);
    const [selected, setSelected] = useState<Set<string>>(new Set());
    const [pending, setPending] = useState(false);
    const [deleteAll, setDeleteAll] = useState(false);
    const [restart, setRestart] = useState<{ id: string; key: string }>();
    const panelRef = useRef<HTMLDivElement>(null);
    const [loadingHeight, setLoadingHeight] = useState<number>();
    const [showLoading, setShowLoading] = useState(false);

    useLayoutEffect(() => {
        setShowLoading(false);
        if (state.loaded || state.error) return;
        const timer = window.setTimeout(() => setShowLoading(true), 100);
        return () => window.clearTimeout(timer);
    }, [state.loaded, state.error, state.archived, state.severity]);


    useEffect(() => {
        setSelected(previous => new Set([...previous].filter(id => state.items.some(item => item.id === id))));
    }, [state.items]);

    async function run(action: () => Promise<unknown>) {
        if (pending) return;
        setPending(true);
        try {
            await action();
            setSelected(new Set());
            await refreshNotifications();
        } catch (error) {
            toast.danger("Notification action failed", {description: error instanceof Error ? error.message : "Please try again."});
        } finally {
            setPending(false);
        }
    }

    function activate(item: NotificationDto, action: NotificationActionDto) {
        if (action.type === "RESTART") {
            setRestart({id: item.id, key: action.key});
        } else if (action.type === "LINK" && action.url) {
            const destination = window.open("about:blank", "_blank");
            if (!destination) {
                toast.danger("Could not open link", {description: "Allow pop-ups for Gameyfin and try again."});
                return;
            }
            destination.opener = null;
            destination.location.replace(action.url);
            void run(() => NotificationEndpoint.executeAction(item.id, action.key));
        } else {
            void run(() => NotificationEndpoint.executeAction(item.id, action.key));
        }
    }

    return <>
        <Popover>
            <Button isIconOnly variant="ghost" aria-label={`Notifications, ${state.unreadCount} unread`}
                    className="relative">
                <BellIcon className="fill-muted" weight={state.unreadCount > 0 ? "fill" : "regular"}/>
                {state.unreadCount > 0 && <span aria-hidden="true"
                                                className="absolute -top-1 -right-1 min-w-5 rounded-full bg-accent px-1 text-xs text-accent-foreground tabular-nums">
                    {state.unreadCount > 99 ? "99+" : state.unreadCount}
                </span>}
            </Button>
            <Popover.Content placement="bottom end" className="max-w-[calc(100vw-1rem)]">
                <Popover.Dialog aria-label="Notifications" className="w-md max-w-[calc(100vw-2rem)]">
                    <Popover.Arrow/>
                    <div className="flex flex-col gap-2 m-2">
                        <Tabs selectedKey={state.archived ? "archive" : "unread"}
                              onSelectionChange={key => {
                                  setLoadingHeight(panelRef.current?.getBoundingClientRect().height);
                                  setSelected(new Set());
                                  showNotificationArchive(key === "archive");
                              }}>
                            <div role="group" aria-label="Notification controls" className="flex items-center gap-1">
                                <Tabs.ListContainer>
                                    <Tabs.List aria-label="Notification view">
                                        <Tabs.Tab id="unread" aria-label="Unread"><span title="Unread"><BellIcon
                                            aria-hidden="true"/></span><Tabs.Indicator/></Tabs.Tab>
                                        <Tabs.Tab id="archive" aria-label="Archive"><span title="Archive"><ArchiveIcon
                                            aria-hidden="true"/></span><Tabs.Indicator/></Tabs.Tab>
                                    </Tabs.List>
                                </Tabs.ListContainer>
                                <Dropdown>
                                    <Button isIconOnly variant={state.severity ? "secondary" : "ghost"}
                                            aria-label={`Filter severity: ${state.severity?.toLowerCase() ?? "all"}`}>
                                        <FunnelIcon weight={state.severity ? "fill" : "regular"}/>
                                    </Button>
                                    <Dropdown.Popover placement="bottom end">
                                        <Dropdown.Menu aria-label="Filter by severity" selectionMode="single"
                                                       selectedKeys={[state.severity ?? "all"]}
                                                       onAction={key => {
                                                           setLoadingHeight(panelRef.current?.getBoundingClientRect().height);
                                                           setSelected(new Set());
                                                           filterNotificationSeverity(key === "all" ? undefined : key as NotificationSeverity);
                                                       }}>
                                            {["all", ...Object.values(NotificationSeverity)].map(severity =>
                                                <Dropdown.Item key={severity} id={severity}
                                                               textValue={severity === "all" ? "All severities" : severity}>
                                                    <span className={severityClasses[severity]}>
                                                        {severity === "all" ? "All severities" : severity.charAt(0) + severity.slice(1).toLowerCase()}
                                                    </span>
                                                    <Dropdown.ItemIndicator/>
                                                </Dropdown.Item>)}
                                        </Dropdown.Menu>
                                    </Dropdown.Popover>
                                </Dropdown>
                                <div className="flex-1"/>
                                <Tooltip>
                                    <ToggleButton isIconOnly variant="ghost" aria-label="Select notifications"
                                                  isSelected={selectMode}
                                                  onChange={enabled => {
                                                      setSelectMode(enabled);
                                                      if (!enabled) setSelected(new Set());
                                                  }}>
                                        <CheckSquareIcon/>
                                    </ToggleButton>
                                    <Tooltip.Content>Select notifications</Tooltip.Content>
                                </Tooltip>
                                <NotificationButton label={state.archived ? "Delete selected" : "Mark selected as read"}
                                                    variant={state.archived ? "danger-soft" : "ghost"}
                                                    isDisabled={pending || selected.size === 0}
                                                    onPress={() => void run(() => state.archived
                                                        ? NotificationEndpoint.deleteArchived([...selected]) : NotificationEndpoint.markRead([...selected]))}>
                                    {state.archived ? <TrashIcon/> : <ListChecksIcon/>}
                                </NotificationButton>
                                <NotificationButton label={state.archived ? "Delete all archived" : "Mark all as read"}
                                                    variant={state.archived ? "danger-soft" : "ghost"}
                                                    isDisabled={pending || !state.loaded || state.total === 0}
                                                    onPress={() => state.archived ? setDeleteAll(true) : void run(() => NotificationEndpoint.markAllRead())}>
                                    {state.archived ? <TrashSimpleIcon/> : <ChecksIcon/>}
                                </NotificationButton>
                                {state.loaded && state.items.length < state.total &&
                                    <NotificationButton label="Load more" onPress={loadMoreNotifications}>
                                        <CaretDoubleDownIcon/>
                                    </NotificationButton>}
                            </div>
                            <Tabs.Panel id={state.archived ? "archive" : "unread"} className="p-0"
                                        ref={panelRef} aria-busy={!state.loaded && !state.error}
                                        style={{minHeight: !state.loaded && !state.error ? loadingHeight : undefined}}>
                                {state.error && <p role="alert" className="text-danger text-sm">{state.error}</p>}
                                {!state.loaded && !state.error && showLoading && <div role="status">
                                    <span className="sr-only">Loading notifications</span>
                                    <div aria-hidden="true"
                                         className="flex items-center gap-3 border-t border-separator pt-3">
                                        {selectMode && <Skeleton className="size-4 shrink-0 rounded-sm"/>}
                                        <div className="min-w-0 flex-1 space-y-2">
                                            <div className="flex justify-between gap-2">
                                                <Skeleton className="h-3 w-12 rounded-sm"/>
                                                <Skeleton className="h-3 w-24 rounded-sm"/>
                                            </div>
                                            <Skeleton className="h-5 w-3/4 rounded-sm"/>
                                            <Skeleton className="h-4 w-full rounded-sm"/>
                                            <Skeleton className="h-4 w-2/3 rounded-sm"/>
                                        </div>
                                        <div className="flex shrink-0 flex-col gap-1">
                                            <Skeleton className="size-10 rounded-md"/>
                                            <Skeleton className="size-10 rounded-md"/>
                                        </div>
                                    </div>
                                </div>}
                                {state.loaded && state.items.length === 0 && <p className="py-5 text-center text-muted">
                                    {state.severity ? "No notifications match this severity." : state.archived ? "No archived notifications." : "No unread notifications."}
                                </p>}
                                <div className="max-h-[60dvh] overflow-y-auto overscroll-contain space-y-3">
                                    {state.items.map(item => <article key={item.id}
                                                                      className="flex items-center gap-3 border-t border-separator pt-3">
                                        {selectMode && <input type="checkbox" className="size-4 shrink-0 accent-current"
                                                              aria-label={`Select ${item.title}`}
                                                              checked={selected.has(item.id)}
                                                              disabled={pending || item.executionState === "RUNNING" || (selected.size >= 100 && !selected.has(item.id))}
                                                              onChange={event => setSelected(previous => {
                                                                  const next = new Set(previous);
                                                                  if (event.target.checked) next.add(item.id); else next.delete(item.id);
                                                                  return next;
                                                              })}/>}
                                        <div className="min-w-0 flex-1 space-y-1">
                                            <div className="flex items-start justify-between gap-2 text-xs">
                                                <span className={`font-semibold ${severityClasses[item.severity]}`}>
                                                    {item.severity.charAt(0) + item.severity.slice(1).toLowerCase()}
                                                </span>
                                                <time dateTime={item.createdAt}
                                                      className="text-right text-muted tabular-nums">
                                                    {new Date(item.createdAt).toLocaleString()}
                                                </time>
                                            </div>
                                            <h3 aria-label={item.title}
                                                className="flex items-center gap-1 font-semibold">
                                                <span className="min-w-0 wrap-anywhere">{item.title}</span>
                                                {item.shared && <span title="Shared admin notification"
                                                                      className="shrink-0 text-muted">
                                                    <ShieldCheckIcon role="img" aria-label="Shared admin notification"/>
                                                </span>}
                                            </h3>
                                            <MarkdownContent className="wrap-anywhere">{item.body}</MarkdownContent>
                                            {item.executionState === "RUNNING" &&
                                                <p role="status" className="text-sm text-muted">Action running…</p>}
                                            {item.executionState === "INTERRUPTED" &&
                                                <p className="text-sm text-warning">Action interrupted. Check its
                                                    outcome
                                                    before
                                                    retrying.</p>}
                                            {item.executionState === "FAILED" &&
                                                <p className="text-sm text-danger">Action failed. You can retry it.</p>}
                                        </div>
                                        <div role="group" aria-label={`Actions for ${item.title}`}
                                             className="flex shrink-0 flex-col items-center gap-1">
                                            {item.actions.map(action => <NotificationButton key={action.key}
                                                                                            label={action.label + (!action.available && item.executionState !== "SUCCEEDED" ? " (unavailable)" : "")}
                                                                                            variant="secondary"
                                                                                            isDisabled={pending || !action.available}
                                                                                            onPress={() => activate(item as NotificationDto, action as NotificationActionDto)}>
                                                {action.type === "RESTART" ? <PowerIcon/> : action.type === "LINK" ?
                                                    <ArrowSquareOutIcon/> : <PlayIcon/>}
                                            </NotificationButton>)}
                                            <NotificationButton variant={state.archived ? "danger-soft" : "ghost"}
                                                                isDisabled={pending || item.executionState === "RUNNING"}
                                                                label={state.archived ? "Delete" : "Mark as read"}
                                                                onPress={() => void run(() => state.archived
                                                                    ? NotificationEndpoint.deleteArchived([item.id]) : NotificationEndpoint.markRead([item.id]))}>
                                                {state.archived ? <TrashIcon/> : <CheckIcon/>}
                                            </NotificationButton>
                                        </div>
                                    </article>)}
                                </div>
                            </Tabs.Panel>
                        </Tabs>
                    </div>
                </Popover.Dialog>
            </Popover.Content>
        </Popover>
        <ConfirmModal isOpen={deleteAll} onOpenChange={setDeleteAll} title="Delete all archived notifications?"
                      confirmLabel="Delete all" isPending={pending} onConfirm={() => void run(async () => {
            await NotificationEndpoint.deleteAllArchived();
            setDeleteAll(false);
        })}>
            <p>This permanently deletes all notifications in your archive, including those on other pages.</p>
            <p>Deleting shared admin notifications removes them for every admin.</p>
        </ConfirmModal>
        <RestartConfirmationModal isOpen={!!restart} onOpenChange={open => {
            if (!open) setRestart(undefined);
        }}
                                  onConfirm={async () => {
                                      if (restart) await NotificationEndpoint.executeAction(restart.id, restart.key);
                                  }}/>
    </>;
}
