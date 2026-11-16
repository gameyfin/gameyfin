import {useState} from "react";
import {Button, Input, Label, Modal, TextField} from "@heroui/react";
import VideoPlayer, {canPlayVideo} from "Frontend/components/general/covers/VideoPlayer";

export default function AddGameVideoModal({onAdd, onClose, existingUrls}: {
    onAdd: (url: string) => void;
    onClose: () => void;
    existingUrls: string[];
}) {
    const [value, setValue] = useState("");
    const [failedUrl, setFailedUrl] = useState("");
    const url = value.trim();
    const supported = canPlayVideo(url);
    const error = !url ? "" : !supported ? "Enter an HTTP(S) video URL supported by the video player."
        : existingUrls.includes(url) ? "This video has already been added." : "";

    function addVideo() {
        if (!url || error) return;
        onAdd(url);
        onClose();
    }

    return <Modal>
        <Modal.Backdrop isOpen onOpenChange={open => {
            if (!open) onClose();
        }} variant="opaque">
            <Modal.Container size="md" placement="center">
                <Modal.Dialog>
                    <Modal.CloseTrigger/>
                    <Modal.Header><Modal.Heading>Add video</Modal.Heading></Modal.Header>
                    <Modal.Body className="flex flex-col gap-4">
                        <TextField value={value} onChange={next => {
                            setValue(next);
                            setFailedUrl("");
                        }}
                                   isInvalid={!!error} autoFocus>
                            <Label>Video URL</Label>
                            <Input placeholder="Paste a video URL" onKeyDown={event => {
                                if (event.key === "Enter") {
                                    event.preventDefault();
                                    event.stopPropagation();
                                    addVideo();
                                }
                            }}/>
                        </TextField>
                        {error && <p role="alert" className="text-sm text-danger">{error}</p>}
                        {supported && <div className="aspect-video overflow-hidden rounded-xl bg-black">
                            <VideoPlayer key={url} src={url} light={false} playing={false}
                                         onError={() => setFailedUrl(url)}/>
                        </div>}
                        {failedUrl === url && url && <p role="alert" className="text-sm text-danger">
                            Unable to load the preview. Check the URL and whether the video allows embedding.
                        </p>}
                    </Modal.Body>
                    <Modal.Footer>
                        <Button type="button" variant="ghost" onPress={onClose}>Cancel</Button>
                        <Button type="button" variant="primary" isDisabled={!url || !!error} onPress={addVideo}>
                            Add video
                        </Button>
                    </Modal.Footer>
                </Modal.Dialog>
            </Modal.Container>
        </Modal.Backdrop>
    </Modal>;
}
