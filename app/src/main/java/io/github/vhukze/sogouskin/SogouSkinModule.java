package io.github.vhukze.sogouskin;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedModule;

/**
 * Sogou IME skin unlock.
 *
 * <p>Paid skins are gated by a local flag on the skin model. Setting
 * {@code payment = 1}, {@code vipOnly = "0"} and {@code newVipType = 0} makes the
 * store treat every skin as owned and hand out the package.
 *
 * <p>The download endpoint shipped in 20.17.0 points at a host that now answers 404,
 * so requests are rewritten to the live skin service that still serves the same path.
 */
public final class SogouSkinModule extends XposedModule {

    private static final String TAG = "SogouSkin";
    private static final String TARGET = "com.sohu.inputmethod.sogou";

    /** Validated against Sogou 20.17.0. Other versions install anyway and degrade by name lookup. */
    private static final long VALIDATED_VERSION_CODE = 2620L;

    private static final String SMART_DETAIL_OWNER = "com.sogou.theme.SmartThemeSkinDetailActivity";
    private static final String SMART_DETAIL_MODEL = "com.sogou.home.theme.bean.SmartThemeDetailModel";
    private static final String PREVIEW_OWNER = "com.sogou.theme.ThemePreviewActivity";
    private static final String PREVIEW_MODEL = "com.sohu.inputmethod.publish.ThemePreviewItem";

    private static final String DEAD_HOST = "download.sginput.qq.com";
    private static final String LIVE_HOST = "srv.android.shouji.sogou.com";

    private final Set<String> logged = ConcurrentHashMap.newKeySet();
    private final Set<ClassLoader> installed = ConcurrentHashMap.newKeySet();
    private boolean targetProcess;

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        String process = param.getProcessName();
        targetProcess = !param.isSystemServer()
                && (TARGET.equals(process) || process.startsWith(TARGET + ":"));
        if (!targetProcess) {
            detach();
            return;
        }
        log(Log.INFO, TAG, "Loaded in " + process + "; API=" + getApiVersion());
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (!targetProcess || !TARGET.equals(param.getPackageName())) {
            return;
        }
        ClassLoader loader = param.getClassLoader();
        if (!installed.add(loader)) {
            return;
        }
        try {
            // onPackageReady precedes Application creation; attach supplies a usable Context.
            Method attach = Application.class.getDeclaredMethod("attach", Context.class);
            hook(attach).setId("SogouSkin#attach").intercept(chain -> {
                Object result = chain.proceed();
                Context context = (Context) chain.getArg(0);
                if (context != null && TARGET.equals(context.getPackageName())) {
                    verifyVersion(context);
                    installHooks(loader);
                }
                return result;
            });
        } catch (ReflectiveOperationException e) {
            log(Log.ERROR, TAG, "Application attach hook failed", e);
        }
    }

    private void verifyVersion(Context context) {
        try {
            long version = context.getPackageManager()
                    .getPackageInfo(TARGET, 0).getLongVersionCode();
            if (version == VALIDATED_VERSION_CODE) {
                log(Log.INFO, TAG, "Version verified: versionCode=" + version);
            } else {
                // Selectors are name based, so a version drift degrades instead of crashing.
                log(Log.WARN, TAG, "Version not verified: versionCode=" + version
                        + ", validated on " + VALIDATED_VERSION_CODE + "; installing hooks anyway");
            }
        } catch (PackageManager.NameNotFoundException e) {
            log(Log.WARN, TAG, "Package version unavailable; installing hooks anyway", e);
        }
    }

    private void installHooks(ClassLoader loader) {
        installAdapter(loader, SMART_DETAIL_OWNER, SMART_DETAIL_MODEL, List.of("T2", "s2", "E2"));
        installAdapter(loader, PREVIEW_OWNER, PREVIEW_MODEL, List.of("L2"));
        installDownloadRedirect(loader);
        log(Log.INFO, TAG, "Hooks installed");
    }

    /** Retired 404 host replaced by the live skin-service host serving the same endpoint. */
    private void installDownloadRedirect(ClassLoader loader) {
        try {
            Class<?> downloader = Class.forName("com.sogou.theme.network.b", false, loader);
            Arrays.stream(downloader.getDeclaredMethods())
                    .filter(m -> m.getName().equals("e"))
                    .filter(m -> {
                        Class<?>[] p = m.getParameterTypes();
                        return p.length == 4 && p[0] == String.class && p[1] == String.class;
                    })
                    .forEach(m -> hook(m).setId("SogouSkin#downloadRedirect")
                            .setExceptionMode(ExceptionMode.PROTECTIVE)
                            .intercept(chain -> {
                                Object[] args = chain.getArgs().toArray();
                                if (args[0] instanceof String url && url.contains(DEAD_HOST)) {
                                    args[0] = url.replace(DEAD_HOST, LIVE_HOST);
                                    log(Log.INFO, TAG, "Redirect download host -> " + LIVE_HOST);
                                }
                                return chain.proceed(args);
                            }));
        } catch (ClassNotFoundException e) {
            log(Log.ERROR, TAG, "Download redirect unavailable", e);
        }
    }

    private void installAdapter(ClassLoader loader, String ownerName, String modelName, List<String> names) {
        try {
            Class<?> owner = Class.forName(ownerName, false, loader);
            Class<?> model = Class.forName(modelName, false, loader);
            Field payment = model.getDeclaredField("payment");
            Field vipOnly = model.getDeclaredField("vipOnly");
            Field newVipType = model.getDeclaredField("newVipType");
            payment.setAccessible(true);
            vipOnly.setAccessible(true);
            newVipType.setAccessible(true);
            Arrays.stream(owner.getDeclaredMethods())
                    .filter(m -> names.contains(m.getName()))
                    .filter(m -> Arrays.equals(m.getParameterTypes(), new Class<?>[]{model}))
                    .forEach(m -> installOwnershipHook(m, payment, vipOnly, newVipType));
        } catch (ReflectiveOperationException | LinkageError e) {
            log(Log.ERROR, TAG, "Adapter unavailable: " + ownerName, e);
        }
    }

    private void installOwnershipHook(Method method, Field payment, Field vipOnly, Field newVipType) {
        try {
            String id = method.getDeclaringClass().getSimpleName() + "#" + method.getName();
            hook(method).setId("SogouSkin#" + id)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object model = chain.getArg(0);
                        if (model != null) {
                            int previous = payment.getInt(model);
                            payment.setInt(model, 1);
                            vipOnly.set(model, "0");
                            newVipType.setInt(model, 0);
                            if (logged.add(id)) {
                                log(Log.INFO, TAG, "Hit " + id + "; payment=" + previous + " -> 1");
                            }
                        }
                        return chain.proceed();
                    });
            log(Log.INFO, TAG, "Installed " + id);
        } catch (RuntimeException | LinkageError e) {
            log(Log.ERROR, TAG, "Hook failed: " + method.getName(), e);
        }
    }
}
