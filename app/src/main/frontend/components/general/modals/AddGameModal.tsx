import {Button, Input, ListBox, Modal, Select, Spinner} from "@heroui/react";
import {useState} from "react";
import {useSnapshot} from "valtio/react";
import {libraryState} from "Frontend/state/LibraryState";
import {GameEndpoint} from "Frontend/generated/endpoints";

interface AddGameModalProps {
    isOpen: boolean;
    onOpenChange: (isOpen: boolean) => void;
    onCreated: (gameId: number) => void;
}

export default function AddGameModal({isOpen, onOpenChange, onCreated}: AddGameModalProps) {
    const libraries = useSnapshot(libraryState).sorted;
    const [title, setTitle] = useState("");
    const [path, setPath] = useState("");
    const [libraryId, setLibraryId] = useState<string | null>(null);
    const [isSaving, setIsSaving] = useState(false);
    const [error, setError] = useState("");

    return (
        <Modal>
            <Modal.Backdrop isOpen={isOpen} onOpenChange={onOpenChange} isDismissable={!isSaving}>
                <Modal.Container size="lg">
                    <Modal.Dialog>
                        {({close}) => (
                            <form onSubmit={async (event) => {
                                event.preventDefault();
                                if (!libraryId || isSaving) return;
                                setIsSaving(true);
                                setError("");
                                try {
                                    const id = await GameEndpoint.createGame(title.trim(), path.trim(), Number(libraryId));
                                    close();
                                    onCreated(id);
                                } catch (e) {
                                    setError(e instanceof Error ? e.message : "Unable to add game");
                                } finally {
                                    setIsSaving(false);
                                }
                            }}>
                                <Modal.Header><Modal.Heading>Add game manually</Modal.Heading></Modal.Header>
                                <Modal.Body className="flex flex-col gap-4">
                                    <p>Enter the game details, then add artwork and other metadata in the editor.</p>
                                    <Input aria-label="Title" placeholder="Title" required value={title}
                                           onChange={e => setTitle(e.target.value)} disabled={isSaving}/>
                                    <Select aria-label="Library" placeholder="Select library" value={libraryId}
                                            onChange={value => setLibraryId(value === null ? null : String(value))}
                                            isDisabled={isSaving}>
                                        <Select.Trigger><Select.Value/><Select.Indicator/></Select.Trigger>
                                        <Select.Popover>
                                            <ListBox>
                                                {libraries.map(library => (
                                                    <ListBox.Item key={library.id} id={String(library.id)} textValue={library.name}>
                                                        {library.name}<ListBox.ItemIndicator/>
                                                    </ListBox.Item>
                                                ))}
                                            </ListBox>
                                        </Select.Popover>
                                    </Select>
                                    <Input aria-label="Path" placeholder="Absolute path to game file or directory" required
                                           value={path} onChange={e => setPath(e.target.value)} disabled={isSaving}/>
                                    {libraries.length === 0 && <p>Create a library before adding games.</p>}
                                    {error && <p role="alert" className="text-danger">{error}</p>}
                                </Modal.Body>
                                <Modal.Footer>
                                    <Button variant="ghost" onPress={close} isDisabled={isSaving}>Cancel</Button>
                                    <Button type="submit" variant="primary" isPending={isSaving}
                                            isDisabled={isSaving || !title.trim() || !path.trim() || !libraryId}>
                                        {isSaving ? <Spinner size="sm" color="current"/> : "Add and edit metadata"}
                                    </Button>
                                </Modal.Footer>
                            </form>
                        )}
                    </Modal.Dialog>
                </Modal.Container>
            </Modal.Backdrop>
        </Modal>
    );
}
