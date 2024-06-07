import {Link} from "react-router-dom";
import {Button} from "@nextui-org/react";
import {toast} from "sonner";

export default function TestView() {
    return (
        <div className="flex grow justify-center mt-12">
            <div className="flex flex-col items-center gap-6">
                <Link to="/setup">Setup</Link>
                <Button onPress={
                    () => toast("Setup finished", {
                        description: "Have fun with Gameyfin!",
                        action: {
                            label: "OK",
                            onClick: () => console.log("Ok"),
                        }
                    })}>Toast</Button>
            </div>
        </div>
    );
}