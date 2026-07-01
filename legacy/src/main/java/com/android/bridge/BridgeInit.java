package com.android.bridge;

import static com.android.bridge.TsBridge.hookAllMethods;
import static com.android.bridge.TsHelpers.callMethod;
import static com.android.bridge.TsHelpers.findAndHookMethod;
import static com.android.bridge.TsHelpers.getObjectField;
import static com.android.bridge.TsHelpers.getParameterIndexByType;
import static com.android.bridge.TsHelpers.setStaticObjectField;

import android.app.ActivityThread;
import android.content.pm.ApplicationInfo;
import android.content.res.Resources;
import android.content.res.ResourcesImpl;
import android.content.res.TypedArray;
import android.content.res.XResources;
import android.os.Build;
import android.os.IBinder;
import android.os.Process;
import android.util.ArrayMap;

import org.matrix.vector.impl.core.VectorDeopter;
import org.matrix.vector.impl.core.VectorModuleManager;
import org.matrix.vector.impl.core.VectorServiceClient;
import org.matrix.vector.impl.utils.VectorModuleClassLoader;
import org.matrix.vector.nativebridge.NativeAPI;
import org.matrix.vector.nativebridge.ResourcesHook;
import org.lsposed.lspd.models.PreLoadedApk;
import org.lsposed.lspd.util.Utils.Log;

import java.io.File;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.android.bridge.callbacks.TsInitPackageResources;
import com.android.bridge.callbacks.BridgeCallback;
import hidden.HiddenApiBridge;

public final class BridgeInit {
    private static final String TAG = TsBridge.TAG;
    public static boolean startsSystemServer = false;

    public static volatile boolean disableResources = false;
    public static AtomicBoolean resourceInit = new AtomicBoolean(false);

    public static void hookResources() throws Throwable {
        if (disableResources || !resourceInit.compareAndSet(false, true)) {
            return;
        }

        VectorDeopter.deoptResourceMethods();

        if (!ResourcesHook.initXResourcesNative()) {
            Log.e(TAG, "Cannot hook resources");
            disableResources = true;
            return;
        }

        findAndHookMethod("android.app.ApplicationPackageManager", null, "getResourcesForApplication",
                ApplicationInfo.class, new TsMethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam<?> param) {
                        ApplicationInfo app = (ApplicationInfo) param.args[0];
                        XResources.setPackageNameForResDir(app.packageName,
                                app.uid == Process.myUid() ? app.sourceDir : app.publicSourceDir);
                    }
                });

        /*
         * getTopLevelResources(a)
         *   -> getTopLevelResources(b)
         *     -> key = new ResourcesKey()
         *     -> r = new Resources()
         *     -> mActiveResources.put(key, r)
         *     -> return r
         */

        final Class<?> classGTLR;
        final Class<?> classResKey;
        final ThreadLocal<Object> latestResKey = new ThreadLocal<>();
        final ArrayList<String> createResourceMethods = new ArrayList<>();

        classGTLR = android.app.ResourcesManager.class;
        classResKey = android.content.res.ResourcesKey.class;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            createResourceMethods.add("createResources");
            createResourceMethods.add("createResourcesForActivity");
        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
            createResourceMethods.add("createResources");
        } else {
            createResourceMethods.add("getOrCreateResources");
        }

        final Class<?> classActivityRes = TsHelpers.findClassIfExists("android.app.ResourcesManager$ActivityResource", classGTLR.getClassLoader());
        var hooker = new TsMethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam<?> param) {
                // At least on OnePlus 5, the method has an additional parameter compared to AOSP.
                Object activityToken = null;
                try {
                    final int activityTokenIdx = getParameterIndexByType(param.method, IBinder.class);
                    activityToken = param.args[activityTokenIdx];
                } catch (NoSuchFieldError ignored) {
                }
                final int resKeyIdx = getParameterIndexByType(param.method, classResKey);
                String resDir = (String) getObjectField(param.args[resKeyIdx], "mResDir");
                XResources newRes = cloneToXResources(param, resDir);
                if (newRes == null) {
                    return;
                }

                //noinspection SynchronizeOnNonFinalField
                synchronized (param.thisObject) {
                    ArrayList<Object> resourceReferences;
                    if (activityToken != null) {
                        Object activityResources = callMethod(param.thisObject, "getOrCreateActivityResourcesStructLocked", activityToken);
                        //noinspection unchecked
                        resourceReferences = (ArrayList<Object>) getObjectField(activityResources, "activityResources");
                    } else {
                        //noinspection unchecked
                        resourceReferences = (ArrayList<Object>) getObjectField(param.thisObject, "mResourceReferences");
                    }
                    if (activityToken == null || classActivityRes == null) {
                        resourceReferences.add(new WeakReference<>(newRes));
                    } else {
                        // Android S createResourcesForActivity()
                        var activityRes = TsHelpers.newInstance(classActivityRes);
                        TsHelpers.setObjectField(activityRes, "resources", new WeakReference<>(newRes));
                        resourceReferences.add(activityRes);
                    }
                }
            }
        };

        for (var createResourceMethod : createResourceMethods) {
            hookAllMethods(classGTLR, createResourceMethod, hooker);
        }

        findAndHookMethod(TypedArray.class, "obtain", Resources.class, int.class,
                new TsMethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam<?> param) throws Throwable {
                        if (param.getResult() instanceof XResources.XTypedArray) {
                            return;
                        }
                        if (!(param.args[0] instanceof XResources)) {
                            return;
                        }
                        XResources.XTypedArray newResult =
                                new XResources.XTypedArray((Resources) param.args[0]);
                        int len = (int) param.args[1];
                        Method resizeMethod = TsHelpers.findMethodBestMatch(
                                TypedArray.class, "resize", int.class);
                        resizeMethod.setAccessible(true);
                        resizeMethod.invoke(newResult, len);
                        param.setResult(newResult);
                    }
                });

        // Replace system resources
        XResources systemRes = new XResources(
                (ClassLoader) TsHelpers.getObjectField(Resources.getSystem(), "mClassLoader"), null);
        HiddenApiBridge.Resources_setImpl(systemRes, (ResourcesImpl) TsHelpers.getObjectField(Resources.getSystem(), "mResourcesImpl"));
        setStaticObjectField(Resources.class, "mSystem", systemRes);

        XResources.init(latestResKey);
    }

    private static XResources cloneToXResources(TsMethodHook.MethodHookParam<?> param, String resDir) {
        Object result = param.getResult();
        if (result == null || result instanceof XResources) {
            return null;
        }

        // Replace the returned resources with our subclass.
        var newRes = new XResources(
                (ClassLoader) TsHelpers.getObjectField(param.getResult(), "mClassLoader"), resDir);
        HiddenApiBridge.Resources_setImpl(newRes, (ResourcesImpl) TsHelpers.getObjectField(param.getResult(), "mResourcesImpl"));

        // Invoke handleInitPackageResources().
        if (newRes.isFirstLoad()) {
            String packageName = newRes.getPackageName();
            TsInitPackageResources.InitPackageResourcesParam resparam = new TsInitPackageResources.InitPackageResourcesParam(TsBridge.sInitPackageResourcesCallbacks);
            resparam.packageName = packageName;
            resparam.res = newRes;
            BridgeCallback.callAll(resparam);
        }

        param.setResult(newRes);
        return newRes;
    }

    // only legacy modules have non-empty value
    private static final Map<String, Optional<String>> loadedModules = new ConcurrentHashMap<>();

    public static Map<String, Optional<String>> getLoadedModules() {
        return loadedModules;
    }

    public static void loadLegacyModules() {
        var moduleList = VectorServiceClient.INSTANCE.getLegacyModulesList();
        moduleList.forEach(module -> {
            var apk = module.apkPath;
            var name = module.packageName;
            var file = module.file;
            loadedModules.put(name, Optional.of(apk)); // temporarily add it for XSharedPreference
            if (!loadModule(name, apk, file)) {
                loadedModules.remove(name);
            }
        });
    }

    public static void loadModules(ActivityThread at) {
        var packages = (ArrayMap<?, ?>) TsHelpers.getObjectField(at, "mPackages");
        VectorServiceClient.INSTANCE.getModulesList().forEach(module -> {
            loadedModules.put(module.packageName, Optional.empty());
            if (!VectorModuleManager.INSTANCE.loadModule(module, startsSystemServer, VectorServiceClient.INSTANCE.getProcessName())) {
                loadedModules.remove(module.packageName);
            } else {
                packages.remove(module.packageName);
            }
        });
    }

    /**
     * Load all so from an APK by reading <code>assets/native_init</code>.
     * It will only store the so names but not doing anything.
     */
    private static void initNativeModule(List<String> moduleLibraryNames) {
        moduleLibraryNames.forEach(NativeAPI::recordNativeEntrypoint);
    }

    private static boolean initModule(ClassLoader mcl, String apk, List<String> moduleClassNames) {
        var count = 0;
        for (var moduleClassName : moduleClassNames) {
            try {
                Log.v(TAG, "  Loading class " + moduleClassName);

                Class<?> moduleClass = mcl.loadClass(moduleClassName);

                if (!IModuleHook.class.isAssignableFrom(moduleClass)) {
                    Log.e(TAG, "    This class doesn't implement any sub-interface of IModuleHook, skipping it");
                    continue;
                }

                final Object moduleInstance = moduleClass.newInstance();

                if (moduleInstance instanceof IZygoteInitHook) {
                    IZygoteInitHook.StartupParam param = new IZygoteInitHook.StartupParam();
                    param.modulePath = apk;
                    param.startsSystemServer = startsSystemServer;
                    ((IZygoteInitHook) moduleInstance).initZygote(param);
                    count++;
                }

                if (moduleInstance instanceof ILoadPackageHook) {
                    TsBridge.hookLoadPackage(new ILoadPackageHook.Wrapper((ILoadPackageHook) moduleInstance));
                    count++;
                }

                if (moduleInstance instanceof IInitPackageResourcesHook) {
                    hookResources();
                    TsBridge.hookInitPackageResources(new IInitPackageResourcesHook.Wrapper((IInitPackageResourcesHook) moduleInstance));
                    count++;
                }
            } catch (Throwable t) {
                Log.e(TAG, "    Failed to load class " + moduleClassName, t);
            }
        }
        return count > 0;
    }

    /**
     * Load a module from an APK by calling the init(String) method for all classes defined
     * in <code>assets/rt_init</code>.
     */
    private static boolean loadModule(String name, String apk, PreLoadedApk file) {
        Log.v(TAG, "Loading legacy module " + name + " from " + apk);

        var sb = new StringBuilder();
        var abis = Process.is64Bit() ? Build.SUPPORTED_64_BIT_ABIS : Build.SUPPORTED_32_BIT_ABIS;
        for (String abi : abis) {
            sb.append(apk).append("!/lib/").append(abi).append(File.pathSeparator);
        }
        var librarySearchPath = sb.toString();

        var initLoader = BridgeInit.class.getClassLoader();
        var mcl = VectorModuleClassLoader.loadApk(apk, file.preLoadedDexes, librarySearchPath, initLoader);

        try {
            if (mcl.loadClass(TsBridge.class.getName()).getClassLoader() != initLoader) {
                Log.e(TAG, "  Cannot load module: " + name);
                Log.e(TAG, "  The Xposed API classes are compiled into the module's APK.");
                Log.e(TAG, "  This may cause strange issues and must be fixed by the module developer.");
                Log.e(TAG, "  For details, see: https://api.xposed.info/using.html");
                return false;
            }
        } catch (ClassNotFoundException ignored) {
            return false;
        }
        initNativeModule(file.moduleLibraryNames);
        return initModule(mcl, apk, file.moduleClassNames);
    }

    public final static Set<String> loadedPackagesInProcess = ConcurrentHashMap.newKeySet(1);
}
