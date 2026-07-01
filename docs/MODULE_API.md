# VectorTai 模块开发

VectorTai 是去特征的 Xposed 框架，API 已改名为中性的 `com.android.bridge.*`，与 [taisuii/LSPlantTai](https://github.com/taisuii/LSPlantTai) 命名一致，模块可在两套间近零成本迁移。**不兼容标准 Xposed 模块**（它们用 `de.robv`/`libxposed`）。

## 类名映射

| 标准 Xposed | VectorTai / LSPlantTai |
| --- | --- |
| `de.robv.android.xposed.XposedBridge` | `com.android.bridge.TsBridge` |
| `…XposedHelpers` | `…TsHelpers` |
| `…XC_MethodHook` | `…TsMethodHook` |
| `…XC_MethodReplacement` | `…TsMethodReplacement` |
| `…XSharedPreferences` | `…TsSharedPreferences` |
| `…IXposedHookLoadPackage` | `…ILoadPackageHook` |
| `…IXposedHookZygoteInit` | `…IZygoteInitHook` |
| `…callbacks.XC_LoadPackage` | `…callbacks.TsLoadPackage` |
| `…callbacks.XCallback` | `…callbacks.BridgeCallback` |

嵌套类 `MethodHookParam` / `LoadPackageParam` 名称不变，方法签名不变——迁移只需改 import 与外层类名。

## 引入 API

从 [Releases](https://github.com/taisuii/VectorTai/releases) 下载 AAR 放进 `libs/`：

```kotlin
repositories { flatDir { dirs("libs") } }
dependencies {
    compileOnly(":VectorTai-legacy-api-v2.2@aar")   // com.android.bridge.*
}
```
> `compileOnly`：API 由框架运行时提供，不打进你的 APK。

## 写一个模块

**1. 入口类**

```java
package com.example.demo;

import com.android.bridge.ILoadPackageHook;
import com.android.bridge.TsBridge;
import com.android.bridge.TsHelpers;
import com.android.bridge.TsMethodHook;
import com.android.bridge.callbacks.TsLoadPackage;

public class MainHook implements ILoadPackageHook {
    @Override
    public void handleLoadPackage(TsLoadPackage.LoadPackageParam lpparam) {
        if (!lpparam.packageName.equals("com.target.app")) return;
        TsHelpers.findAndHookMethod("com.target.app.Foo", lpparam.classLoader,
                "bar", String.class, new TsMethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                param.args[0] = "hooked";
                TsBridge.log("bar() intercepted");
            }
        });
    }
}
```

**2. 入口清单** `assets/rt_init`（一行一个全限定类名）：

```
com.example.demo.MainHook
```

**3. 声明模块** `AndroidManifest.xml` 的 `<application>` 内：

```xml
<meta-data android:name="rt.min.version" android:value="82" />
```

## 元数据契约

| 键 | 位置 | 必需 | 说明 |
| --- | --- | --- | --- |
| `rt.min.version` | manifest meta-data | 是 | 模块标记 + 最低 API 版本（整数，沿用 Xposed 语义，如 82/93） |
| `rt.scope` | manifest meta-data | 否 | 默认作用域，string-array 资源 id |
| `rt.shared.prefs` | manifest meta-data | 否 | 布尔，启用远程共享 SharedPreferences |
| `assets/rt_init` | 资产文件 | 是 | 入口类清单（一行一个 FQN） |
| `assets/native_init` | 资产文件 | 否 | 原生 .so 入口清单 |

## 安装与启用

1. 模块编译成普通 APK 安装。
2. 打开 VectorTai 管理器 → 勾选模块 → 选作用域 → 重启目标应用（或重启）。

## 现代 API（可选）

`compileOnly(":VectorTai-modern-api-v2.2@aar")`，入口类 `extends dev.android.runtime.api.XposedModule`，配置放 `src/main/resources/META-INF/xposed/`（`module.prop` 里 `targetApiVersion=101`、`java_init.list` 列入口类），manifest 同样需 `rt.min.version`。
> 现代 API 的 `dev.android.runtime.api.*` 类名与 `META-INF/xposed/` 路径尚含 `xposed` 字样，未完全去特征；追求彻底去特征请用上面的 `com.android.bridge.*`（传统）API。

## 从源码构建

```bash
git submodule update --init --recursive
python3 rename_api.py          # 复现 libxposed 子模块（现代 API）改名
./gradlew :legacy:assembleRelease :xposed:assembleRelease
```
