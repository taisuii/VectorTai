package com.android.bridge.callbacks;

import android.content.res.XResources;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.CopyOnWriteArraySet;

import com.android.bridge.IInitPackageResourcesHook;
import dev.android.runtime.api.XposedModuleInterface;

/**
 * This class is only used for internal purposes, except for the {@link InitPackageResourcesParam}
 * subclass.
 */
public abstract class TsInitPackageResources extends BridgeCallback implements IInitPackageResourcesHook {
    /**
     * Creates a new callback with default priority.
     *
     * @hide
     */
    @SuppressWarnings("deprecation")
    public TsInitPackageResources() {
        super();
    }

    /**
     * Creates a new callback with a specific priority.
     *
     * @param priority See {@link BridgeCallback#priority}.
     * @hide
     */
    public TsInitPackageResources(int priority) {
        super(priority);
    }

    /**
     * Wraps information about the resources being initialized.
     */
    public static final class InitPackageResourcesParam extends BridgeCallback.Param {
        /**
         * @hide
         */
        public InitPackageResourcesParam(CopyOnWriteArraySet<TsInitPackageResources> callbacks) {
            super(callbacks.toArray(new BridgeCallback[0]));
        }

        /**
         * The name of the package for which resources are being loaded.
         */
        public String packageName;

        /**
         * Reference to the resources that can be used for calls to
         * {@link XResources#setReplacement(String, String, String, Object)}.
         */
        public XResources res;
    }

    /**
     * @hide
     */
    @Override
    protected void call(Param param) throws Throwable {
        if (param instanceof InitPackageResourcesParam)
            handleInitPackageResources((InitPackageResourcesParam) param);
    }
}
