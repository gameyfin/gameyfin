import React, {useState} from "react";
import {PlugIcon} from "@phosphor-icons/react";
import MarketplacePluginDto from "Frontend/generated/org/gameyfin/app/core/plugins/marketplace/dto/MarketplacePluginDto";

interface MarketplacePluginLogoProps {
    plugin: MarketplacePluginDto;
    size?: number;
    glow?: boolean;
}

export default function MarketplacePluginLogo({plugin, size = 64, glow = false}: MarketplacePluginLogoProps) {
    const [failed, setFailed] = useState(false);

    if (!plugin.hasLogo || failed) {
        return <PlugIcon size={size} weight="fill"/>;
    }

    const src = `/images/marketplace/${encodeURIComponent(plugin.marketplaceId)}/${encodeURIComponent(plugin.id)}/logo`;

    return (
        <span className="relative inline-flex" style={{width: size, height: size}}>
            {glow && <img className="absolute inset-0 size-full object-contain blur-lg opacity-70" src={src}
                          alt="" aria-hidden="true" loading="lazy"/>}
            <img className="relative size-full object-contain" src={src} alt={plugin.name} loading="lazy"
                 onError={() => setFailed(true)}/>
        </span>
    );
}
