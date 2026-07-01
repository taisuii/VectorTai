package org.matrix.vector;

import org.lsposed.lspd.service.ILSPApplicationService;
import org.lsposed.lspd.util.Utils;
import org.matrix.vector.impl.core.VectorStartup;
import org.matrix.vector.impl.di.VectorBootstrap;
import org.matrix.vector.legacy.LegacyDelegateImpl;

import com.android.bridge.TsBridge;
import com.android.bridge.BridgeInit;

public class Startup {

    public static void bootstrapXposed(boolean systemServerStarted) {
        try {
            VectorStartup.bootstrap(BridgeInit.startsSystemServer, systemServerStarted);
            BridgeInit.loadLegacyModules();
        } catch (Throwable t) {
            Utils.logE("Error during framework initialization", t);
        }
    }

    public static void initXposed(boolean isSystem, String processName, String appDir, ILSPApplicationService service) {
        // Establish the Dependency Injection contract
        VectorBootstrap.INSTANCE.init(new LegacyDelegateImpl());

        // Initialize legacy resources and state
        TsBridge.initXResources();
        BridgeInit.startsSystemServer = isSystem;

        // Hand off execution to the modern framework initialization
        VectorStartup.init(isSystem, processName, appDir, service);
    }
}
