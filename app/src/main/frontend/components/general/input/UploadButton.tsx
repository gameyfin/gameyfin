import {useRef, ComponentProps} from "react";
import {Button, Spinner} from "@heroui/react";
import {UploadSimpleIcon} from "@phosphor-icons/react";

type Props = Omit<ComponentProps<typeof Button>, "onPress" | "type" | "children"> & {
    label: string;
    accept?: string;
    multiple?: boolean;
    onFilesSelected: (files: File[]) => void | Promise<void>;
};

export default function UploadButton({label, accept, multiple, onFilesSelected, isDisabled, isPending, variant = "primary", ...props}: Props) {
    const input = useRef<HTMLInputElement>(null);
    return <>
        <Button {...props} type="button" variant={variant} isDisabled={isDisabled || isPending}
                isPending={isPending} onPress={() => input.current?.click()}>
            {isPending ? <Spinner size="sm" color="current"/> : <UploadSimpleIcon/>}
            {label}
        </Button>
        <input ref={input} type="file" accept={accept} multiple={multiple} className="hidden"
               aria-label={label} disabled={isDisabled || isPending}
               onChange={event => {
                   const files = Array.from(event.target.files ?? []);
                   event.target.value = "";
                   if (files.length) void onFilesSelected(files);
               }}/>
    </>;
}
