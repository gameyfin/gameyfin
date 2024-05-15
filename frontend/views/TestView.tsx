import {Link} from "react-router-dom";

export default function TestView() {
    return (
        <div className="flex grow justify-center">
            <Link to="/setup">Setup</Link>
        </div>
    );
}