package com.example.addon;

import autismclient.api.AutismAddon;
import autismclient.api.AutismAddons;
import autismclient.api.ApiVersion;

import com.example.addon.modules.*;

import autismclient.api.macro.MacroActionEntry;
import com.example.addon.macro.ExtractUsernameAction;

public final class ExampleAddon extends AutismAddon {
    public static final String ID = "autismclient-pvpplus";

    @Override
    public int apiVersion() {
        return ApiVersion.CURRENT;
    }

    @Override
    public void onInitialize() {
        AutismAddons.modules().register(new AutoTotem());
        AutismAddons.modules().register(new HoverTotem());
        AutismAddons.modules().register(new AutoCrystal());
        AutismAddons.modules().register(new AutoAnchor());
        
        AutismAddons.modules().register(new AimAssistPlus());
        AutismAddons.modules().register(new BetterBoatfly());
        AutismAddons.modules().register(new FastPlace());
        AutismAddons.modules().register(new PearlCatch());
        AutismAddons.modules().register(new WTap());
        AutismAddons.modules().register(new AntiHunger());
        AutismAddons.modules().register(new SeedXRay());

        AutismAddons.modules().register(new SnapTap());
        AutismAddons.modules().register(new AutoCrit());
        AutismAddons.modules().register(new MaceSwap());

        AutismAddons.macroActions().register(
            MacroActionEntry.builder(ExtractUsernameAction.TYPE_ID, ExtractUsernameAction::new)
                .schema(new ExtractUsernameAction().schema())
                .picker("Flow", "Extract Username", "Extracts usernames from Tablist or Autofill")
                .build()
        );
    }

    @Override
    public String getPackage() {
        return "com.example.addon";
    }
}
