import {Middleware, MiddlewareContext, MiddlewareNext} from '@hilla/frontend';
import {toast} from "sonner";
import {getReasonPhrase} from "http-status-codes";

export const ErrorHandlingMiddleware: Middleware = async function(
    context: MiddlewareContext,
    next: MiddlewareNext
) {
    const {endpoint, method} = context;

    let response: Response = await next(context);
    if(!response.ok) {
        //Ignore calls to UserEndpoint.getUserInfo since they are managed by Hilla and called on initial load
        if(endpoint == "UserEndpoint" && method == "getUserInfo") return response;

        toast.error(`${getReasonPhrase(response.status)}`, {description: `${endpoint}.${method}`})
    }

    return response;
}