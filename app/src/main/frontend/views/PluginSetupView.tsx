import {useEffect, useState} from "react";
import {Button, Spinner, toast} from "@heroui/react";
import {PluginOnboardingEndpoint} from "Frontend/generated/endpoints";
import PluginMarketplace from "Frontend/components/general/plugin/marketplace/PluginMarketplace";
import ActiveTaskPopover from "Frontend/components/general/ActiveTaskPopover";
import {useAuth} from "Frontend/util/auth";

export default function PluginSetupView() {
    const {state} = useAuth();
    const [discovered, setDiscovered] = useState(false);
    const [pending, setPending] = useState<boolean>();
    const [finishing, setFinishing] = useState(false);
    const [error, setError] = useState<string>();
    const admin = state.user?.roles.some(role => role === "ROLE_ADMIN" || role === "ROLE_SUPERADMIN");

    useEffect(() => {
        if (!admin) return;
        PluginOnboardingEndpoint.isPending().then(value => {
            if (!value) window.location.href = "/login/success";
            else setPending(true);
        }).catch(e => setError(e?.message ?? "Could not load plugin setup. Reload to retry."));
    }, [admin]);

    useEffect(() => {
        if (!admin) return;
        let stopped = false;
        let timer: ReturnType<typeof setTimeout>;

        async function poll() {
            try {
                const ready = await PluginOnboardingEndpoint.isPluginDiscoveryComplete();
                if (stopped) return;
                if (ready) setDiscovered(true);
                else timer = setTimeout(poll, 1000);
            } catch {
                if (!stopped) timer = setTimeout(poll, 3000);
            }
        }

        void poll();
        return () => {
            stopped = true;
            clearTimeout(timer);
        };
    }, [admin]);

    async function finish() {
        setFinishing(true);
        try {
            await PluginOnboardingEndpoint.complete();
            window.location.href = "/login/success";
        } catch (e: any) {
            toast.danger("Could not finish plugin setup", {description: e?.message});
            setFinishing(false);
        }
    }

    if (!admin) return <p className="p-8">Administrator access required.</p>;
    if (error) return <p role="alert" className="p-8">{error}</p>;
    if (!pending) return <div className="p-8"><Spinner/></div>;

    return <main className="max-w-7xl mx-auto p-6 flex flex-col gap-6">
        <div className="flex items-center justify-between">
            <h1 className="text-3xl font-bold">Choose your plugins</h1>
            <div className="flex items-center gap-2"><span>Installation progress and history</span><ActiveTaskPopover/>
            </div>
        </div>
        <p>Plugins add metadata, downloads, and store support. Install them individually, then review their
            configuration and enable them below.<br/>
            You can also skip this step for now and manage plugins later in Administration.</p>
        {discovered ? <PluginMarketplace/> : <p>Discovering existing plugins… You can finish setup now.</p>}
        <Button className="self-end" onPress={finish} isPending={finishing}>Finish setup</Button>
    </main>;
}
