export interface EditorSnapshot {
    value: string;
    selection: {start: number; end: number};
}

/** Shared history for native textarea edits and programmatic formatting. */
export class MarkdownHistory {
    private past: EditorSnapshot[] = [];
    private future: EditorSnapshot[] = [];
    private current: EditorSnapshot;
    private group = "";
    private lastEdit = 0;

    constructor(value: string) {
        this.current = {value, selection: {start: 0, end: 0}};
    }

    sync(value: string) {
        if (value === this.current.value) return;
        this.current = {value, selection: {start: 0, end: 0}};
        this.past = [];
        this.future = [];
        this.group = "";
    }

    record(before: EditorSnapshot, after: EditorSnapshot, group = "", now = Date.now()) {
        this.sync(before.value);
        if (before.value === after.value) return;
        const contiguous = this.current.selection.start === before.selection.start
            && this.current.selection.end === before.selection.end;
        if (!group || group !== this.group || now - this.lastEdit > 750 || !contiguous) {
            this.past.push(before);
            if (this.past.length > 100) this.past.shift();
        }
        this.current = after;
        this.future = [];
        this.group = group;
        this.lastEdit = now;
    }

    undo(selection: EditorSnapshot["selection"]) {
        const previous = this.past.pop();
        if (!previous) return undefined;
        this.future.push({...this.current, selection});
        this.current = previous;
        this.group = "";
        return previous;
    }

    redo(selection: EditorSnapshot["selection"]) {
        const next = this.future.pop();
        if (!next) return undefined;
        this.past.push({...this.current, selection});
        this.current = next;
        this.group = "";
        return next;
    }
}
