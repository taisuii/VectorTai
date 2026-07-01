# VectorTai 模块开发

VectorTai 是去特征的 Xposed 框架，API 已改名。模块须针对本 API 编译，**不兼容标准 Xposed 模块**。

## 包名映射

| 标准 Xposed | VectorTai |
| --- | --- |
| `de.robv.android.xposed.*` | `dev.android.runtime.ext.*` |
| `io.github.libxposed.api.*` | `dev.android.runtime.api.*` |
| `io.github.libxposed.service.*` | `dev.android.runtime.service.*` |

## 引入 API

从 [Releases](https://github.com/taisuii/VectorTai/releases) 下载 AAR 放进 `libs/`：

```kotlin
repositories { flatDir { dirs("libs") } }
dependencies {
    compileOnly(":VectorTai-legacy-api-v2.1@aar")   // 传统 API
    // compileOnly(":VectorTai-modern-api-v2.1@aar") // 现代 API
}
```
> `compileOnly`：API 由框架在运行时提供，不打进你的 APK。

## 传统模块（推荐）

**1. 入口类**

```java
package com.example.demo;

import dev.android.runtime.ext.IXposedHookLoadPackage;
import dev.android.runtime.ext.XposedBridge;
import dev.android.runtime.ext.XposedHelpers;
import dev.android.runtime.ext.XC_MethodHook;
import dev.android.runtime.ext.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!lpparam.packageName.equals("com.target.app")) return;
        XposedHelpers.findAndHookMethod("com.target.app.Foo", lpparam.classLoader,
                "bar", String.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                param.args[0] = "hooked";
                XposedBridge.log("bar() intercepted");
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

## 现代模块

入口类 `extends dev.android.runtime.api.XposedModule`，并在 `src/main/resources/META-INF/xposed/` 放：
- `module.prop`：`targetApiVersion=101`（须 ≥101，100 已弃用）、`minApiVersion=101`
- `java_init.list`：入口类全限定名
- `scope.list`（可选）：目标包名，一行一个

Manifest 同样需 `rt.min.version` meta-data 才会被管理器识别。
> 注意：现代模块的 `META-INF/xposed/` 路径未去特征，仍含 `xposed` 字样。

## 元数据契约

| 键 | 位置 | 必需 | 说明 |
| --- | --- | --- | --- |
| `rt.min.version` | manifest meta-data | 是 | 模块标记 + 最低 API 版本（整数，沿用 Xposed 语义，如 82/93） |
| `rt.scope` | manifest meta-data | 否 | 默认作用域，string-array 资源 id |
| `rt.shared.prefs` | manifest meta-data | 否 | 布尔，启用远程共享 SharedPreferences |
| `xposeddescription` | manifest meta-data | 否 | 模块描述（此键暂未改名） |
| `assets/rt_init` | 资产文件 | 传统模块必需 | 入口类清单 |
| `assets/native_init` | 资产文件 | 否 | 原生 .so 入口清单 |

## 安装与启用

1. 把模块编译成普通 APK 安装。
2. 打开 VectorTai 管理器 → 勾选模块 → 选作用域 → 重启目标应用（或重启）。

## 从源码构建 API

```bash
git submodule update --init --recursive
python3 rename_api.py          # 把 libxposed 子模块改名，复现去特征
./gradlew :legacy:assembleRelease :xposed:assembleRelease
```
