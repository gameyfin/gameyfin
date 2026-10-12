import {Middleware, MiddlewareContext, MiddlewareNext} from '@vaadin/hilla-frontend';
import {toast} from "@heroui/react";

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

        if (json.type == "dev.hilla.exception.EndpointException" || json.type == "com.vaadin.hilla.exception.EndpointException") {
            toast.danger("Error", {
                description: json.message
            })
        } else {
            toast.danger("Error", {
                description: `${endpoint}.${method}`
            })
        }
    }

    return originalResponse;
}