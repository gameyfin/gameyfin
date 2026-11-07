import {useEffect, useRef, useState} from "react";
import {useField} from "formik";
import {Button, Tabs, Toolbar, Tooltip} from "@heroui/react";
import {
    CodeBlockIcon, CodeIcon, LinkSimpleIcon, ListBulletsIcon, ListNumbersIcon,
    QuotesIcon, TextBIcon, TextHTwoIcon, TextItalicIcon, type Icon
} from "@phosphor-icons/react";
import TextAreaInput, {TextAreaInputProps} from "./TextAreaInput";
import MarkdownContent from "../MarkdownContent";

import {formatMarkdown, type Action} from "./markdownFormatting";
import {MarkdownHistory, type EditorSnapshot} from "./markdownHistory";

const actions: [Action, string, Icon][] = [
    ["bold", "Bold", TextBIcon], ["italic", "Italic", TextItalicIcon], ["heading", "Heading", TextHTwoIcon],
    ["link", "Link", LinkSimpleIcon], ["bullet", "Bulleted list", ListBulletsIcon], ["number", "Numbered list", ListNumbersIcon],
    ["quote", "Blockquote", QuotesIcon], ["code", "Inline code", CodeIcon], ["block", "Code block", CodeBlockIcon]
];

export default function MarkdownInput(props: Omit<TextAreaInputProps, "toolbar" | "preview" | "textareaRef" | "textareaProps" | "renderEditor">) {
    const [field, , helpers] = useField<string>(props.name);
    const textarea = useRef<HTMLTextAreaElement>(null);
    const selection = useRef({start: 0, end: 0});
    const beforeInput = useRef({start: 0, end: 0});
    const history = useRef(new MarkdownHistory(field.value ?? ""));
    const [preview, setPreview] = useState(false);
    const disabled = props.isDisabled || props.isReadOnly || preview;

    // Metadata source changes and form resets start a new editing history.
    useEffect(() => {
        history.current.sync(field.value ?? "");
    }, [field.value]);

    function applySnapshot(snapshot: EditorSnapshot) {
        void helpers.setValue(snapshot.value);
        selection.current = snapshot.selection;
        requestAnimationFrame(restoreSelection);
    }

    function format(action: Action) {
        if (disabled || !textarea.current) return;
        const value = field.value ?? "";
        const result = formatMarkdown(value, selection.current, action);
        history.current.record({value, selection: {...selection.current}}, result);
        applySnapshot(result);
    }

    function restoreSelection() {
        textarea.current?.focus();
        textarea.current?.setSelectionRange(selection.current.start, selection.current.end);
    }

    return <TextAreaInput {...props} textareaRef={textarea}
                          textareaProps={{
                              rows: 6,
                              className: "block w-full min-h-40 resize-y rounded-none border-0 bg-transparent shadow-none focus:ring-0 focus-visible:ring-0",
                              onBeforeInput: event => {
                                  beforeInput.current = {
                                      start: event.currentTarget.selectionStart,
                                      end: event.currentTarget.selectionEnd
                                  };
                              },
                              onChange: event => {
                                  const input = event.currentTarget;
                                  const inputType = (event.nativeEvent as InputEvent).inputType;
                                  const group = ["insertText", "insertCompositionText", "deleteContentBackward", "deleteContentForward"].includes(inputType) ? inputType : "";
                                  history.current.record(
                                      {value: field.value ?? "", selection: {...beforeInput.current}},
                                      {
                                          value: input.value,
                                          selection: {start: input.selectionStart, end: input.selectionEnd}
                                      }, group);
                                  selection.current = {start: input.selectionStart, end: input.selectionEnd};
                              },
                              onSelect: event => {
                                  selection.current = {
                                      start: event.currentTarget.selectionStart,
                                      end: event.currentTarget.selectionEnd
                                  };
                                  beforeInput.current = {...selection.current};
                              },
                              onKeyDown: event => {
                                  beforeInput.current = {
                                      start: event.currentTarget.selectionStart,
                                      end: event.currentTarget.selectionEnd
                                  };
                                  if (!(event.ctrlKey || event.metaKey) || event.altKey || event.nativeEvent.isComposing || disabled) return;
                                  const key = event.key.toLowerCase();
                                  if (key === "z" || key === "y") {
                                      event.preventDefault();
                                      history.current.sync(field.value ?? "");
                                      const snapshot = key === "y" || event.shiftKey
                                          ? history.current.redo(beforeInput.current) : history.current.undo(beforeInput.current);
                                      if (snapshot) applySnapshot(snapshot);
                                      return;
                                  }
                                  const action = ({
                                      b: "bold",
                                      i: "italic",
                                      k: "link"
                                  } as const)[event.key.toLowerCase() as "b" | "i" | "k"];
                                  if (action && !disabled) {
                                      event.preventDefault();
                                      format(action);
                                  }
                              }
                          }}
                          renderEditor={control => <Tabs selectedKey={preview ? "preview" : "write"}
                                                         onSelectionChange={key => setPreview(key === "preview")}
                                                         isDisabled={props.isDisabled}
                                                         variant="secondary"
                                                         className="gap-0 overflow-hidden rounded-field border border-border bg-field shadow-field focus-within:border-accent data-[disabled=true]:opacity-50 has-data-[invalid=true]:border-danger">
                              <div
                                  className="flex flex-wrap items-center justify-between gap-x-2 border-b border-separator bg-surface-secondary px-2 pt-1">
                                  <Tabs.ListContainer className="shrink-0 rounded-none bg-transparent">
                                      <Tabs.List aria-label={`${props.label ?? props.name} editor mode`}>
                                          <Tabs.Tab id="write">Write<Tabs.Indicator/></Tabs.Tab>
                                          <Tabs.Tab id="preview">Preview<Tabs.Indicator/></Tabs.Tab>
                                      </Tabs.List>
                                  </Tabs.ListContainer>
                                  {!preview && <Toolbar aria-label={`${props.label ?? props.name} formatting`}
                                                        className="flex flex-wrap gap-0 pb-1">
                                      {actions.map(([action, label, ActionIcon]) => <Tooltip key={action}>
                                          <Button isIconOnly type="button" size="sm" variant="ghost"
                                                  aria-label={label} isDisabled={disabled}
                                                  onPress={() => format(action)}><ActionIcon size={18}
                                                                                             aria-hidden="true"/></Button>
                                          <Tooltip.Content>{label}</Tooltip.Content>
                                      </Tooltip>)}
                                  </Toolbar>}
                              </div>
                              <Tabs.Panel id="write" shouldForceMount className="p-0 data-inert:hidden">
                                  {control}
                              </Tabs.Panel>
                              <Tabs.Panel id="preview" className="min-h-40 p-3 text-sm">
                                  {field.value?.trim() ? <MarkdownContent>{field.value}</MarkdownContent> :
                                      <p className="text-muted">Nothing to preview</p>}
                              </Tabs.Panel>
                          </Tabs>}/>;
}
