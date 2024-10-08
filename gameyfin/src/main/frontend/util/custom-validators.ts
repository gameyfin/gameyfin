import * as Yup from "yup";
import {isValidCron} from "cron-validator";

// Custom validator for cron expressions
Yup.addMethod(Yup.string, 'cron', function (message) {
    return this.test('cron', message, function (value) {
        const {path, createError} = this;
        return isValidCron(value as string) || createError({path, message: message || 'Invalid cron expression'});
    });
});