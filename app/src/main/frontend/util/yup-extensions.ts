import * as Yup from 'yup';
import {ConfigEndpoint} from 'Frontend/generated/endpoints';

Yup.addMethod(Yup.string, 'cron', function (message = 'Invalid cron expression') {
    return this.test('cron', message, async function (value) {
        const {path, createError} = this;
        if (!value) return true;
        try {
            const isValid = await ConfigEndpoint.validateCronExpression(value);
            return isValid || createError({path, message});
        } catch (e) {
            return createError({path, message: 'Error validating cron expression'});
        }
    });
});

// TypeScript: Extend Yup's type definitions
declare module 'yup' {
    interface StringSchema {
        cron(message?: string): StringSchema;
    }
}