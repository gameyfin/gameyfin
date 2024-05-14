import {Link} from "react-router-dom";

export default function TestView() {
    return (
        <div className="size-full flex justify-center">
            <Link to="/setup">Setup</Link>
        </div>
    );
}