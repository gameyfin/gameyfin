import {ComponentProps} from "react";
import {Table} from "@heroui/react";

/** Dense secondary tables deliberately scroll locally, with a visible compact-screen cue. */
export default function TableScroller({label, className = "", children, ...props}:
                                          ComponentProps<typeof Table.ScrollContainer> & { label: string }) {
    return <div className="table-region">
        <p className="table-region-hint">Scroll horizontally to see all columns.</p>
        <Table.ScrollContainer {...props} role="region" aria-label={label} tabIndex={0}
                               className={`table-scroll-region ${className}`}>{children}</Table.ScrollContainer>
    </div>;
}
