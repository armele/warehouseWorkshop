package com.deathfrog.warehouseworkshop;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import com.deathfrog.warehouseworkshop.core.client.ResearchSuppliesClientHooks;
import com.deathfrog.warehouseworkshop.core.client.gui.modules.WindowResearchLedger;
import com.deathfrog.warehouseworkshop.core.client.gui.modules.WindowResearchSupplies;
import com.deathfrog.warehouseworkshop.core.client.network.ClientResearchSuppliesHandler;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = WarehouseWorkshopMod.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = WarehouseWorkshopMod.MODID, value = Dist.CLIENT)
public class WarehouseWorkshopModClient {
    public WarehouseWorkshopModClient(ModContainer container) {
        ResearchSuppliesClientHooks.register(
            position -> new WindowResearchLedger(position).open(),
            WindowResearchSupplies::new,
            ClientResearchSuppliesHandler::handle);

        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // Some client setup code
        WarehouseWorkshopMod.LOGGER.info("HELLO FROM CLIENT SETUP");
        WarehouseWorkshopMod.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }
}
