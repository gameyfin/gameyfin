import {useEffect} from "react";
import {useLocation} from "react-router";
import {useAuth} from "Frontend/util/auth";
import {redirectToLogin} from "Frontend/util/login";

/** Bridges Hilla's client-side route protection to the server's login gateway. */
export default function LoginRedirectView() {
    const {state} = useAuth();
    const location = useLocation();

    useEffect(() => {
        if (state.initializing || state.loading) return;
        if (state.user) {
            // Authentication cannot grant an existing user a missing role.
            window.location.href = "/";
            return;
        }
        const from = location.state?.from;
        redirectToLogin(from ? from.pathname + from.search + from.hash : "/");
    }, [state.initializing, state.loading, state.user, location.state]);

    return null;
}
