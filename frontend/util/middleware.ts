import {Middleware, MiddlewareContext, MiddlewareNext} from '@hilla/frontend';
import {toast} from "sonner";
import {getReasonPhrase} from "http-status-codes";

export const ErrorHandlingMiddleware: Middleware = async function (
    context: MiddlewareContext,
    next: MiddlewareNext
) {
    const {endpoint, method} = context;

    let originalResponse = (await next(context));

    if (!originalResponse.ok) {
        // .clone() is necessary because response.json() is one-time only and Hilla accesses it in its internal error handler
        // @see https://developer.mozilla.org/en-US/docs/Web/API/Response/clone
        let response: Response = originalResponse.clone();

        //Ignore calls to UserEndpoint.getUserInfo since they are managed by Hilla and called on initial load
        if (endpoint == "UserEndpoint" && method == "getUserInfo") return originalResponse;

        let json: any = await response.json();

        if (json.type == "dev.hilla.exception.EndpointException") {
            toast.error(`${getReasonPhrase(response.status)}`, {description: `${json.message}`});
        } else {
            toast.error(`${getReasonPhrase(response.status)}`, {description: `${endpoint}.${method}`})
        }
    }

    return originalResponse;
}