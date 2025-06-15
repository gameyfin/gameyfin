import React from 'react';

// @ts-ignore
export function SmallInfoField({icon: IconComponent, message, ...props}) {
    return (
        <div {...props}>
            <small className="flex flex-row items-center gap-1">
                <IconComponent weight="fill" size={14}/> {message}
            </small>
        </div>
    );
}