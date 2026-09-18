package com.kaleblangley.lockscontinue;

import com.kaleblangley.lockscontinue.config.LocksContinueConfig;
import com.kaleblangley.lockscontinue.item.ModCreativeModeTabs;
import com.kaleblangley.lockscontinue.item.ModItems;
import com.kaleblangley.lockscontinue.network.LockNetwork;
import com.kaleblangley.lockscontinue.sound.ModSounds;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



@Mod(LocksContinue.MODID)
public class LocksContinue {
    public static final String MODID = "lockscontinue";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);
    public LocksContinue(){
        LocksContinueConfig.register();
        IEventBus modBusEvent = FMLJavaModLoadingContext.get().getModEventBus();
        LockNetwork.register();
        ModItems.register(modBusEvent);
        ModSounds.register(modBusEvent);
        ModCreativeModeTabs.register(modBusEvent);
    }
}
