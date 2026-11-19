package net.ashpapi.papioptimization.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PapiConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("Gluon/Config");
    private static final String FILE_NAME = "gluon.properties";

    private static volatile boolean loaded = false;

    private static volatile boolean entityCulling = true;
    private static volatile boolean particleCulling = true;
    private static volatile boolean ebe = true;
    private static volatile boolean ebeSodiumAdapter = true;
    private static volatile boolean ebeVitrailAdapter = true;
    private static volatile boolean moreCulling = true;
    private static volatile boolean guiBatching = true;
    private static volatile boolean badOptimizations = true;
    private static volatile boolean alternateCurrent = true;
    private static volatile boolean lithiumDeep = true;
    private static volatile boolean noisiumC2me = true;
    private static volatile boolean fastFurnace = true;
    private static volatile boolean clumps = true;
    private static volatile boolean serverCoreMspt = true;
    private static volatile boolean ferriteCore = true;
    private static volatile boolean modernFix = true;
    private static volatile boolean krypton = true;

    private static volatile double msptTarget = 45.0;
    private static volatile double particleLodDistance = 30.0;

    private PapiConfig() {}

    public static void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (PapiConfig.class) {
            if (loaded) {
                return;
            }
            loadInternal();
            loaded = true;
        }
    }

    private static Path resolveConfigPath() {
        try {
            Path dir = FMLPaths.CONFIGDIR.get();
            if (dir != null) {
                return dir.resolve(FILE_NAME);
            }
        } catch (Throwable ignored) {
        }
        return Path.of("config", FILE_NAME);
    }

    private static void loadInternal() {
        Path path = resolveConfigPath();
        Properties props = new Properties();
        boolean fileExisted = Files.isRegularFile(path);
        boolean needsWrite = !fileExisted;

        if (fileExisted) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                props.load(reader);
            } catch (Throwable t) {
                LOGGER.warn("Failed to read {}; using defaults.", path, t);
                props.clear();
                needsWrite = true;
            }
        }

        boolean[] modified = new boolean[]{needsWrite};
        entityCulling = readBool(props, "entityCulling", true, modified);
        particleCulling = readBool(props, "particleCulling", true, modified);
        ebe = readBool(props, "ebe", true, modified);
        ebeSodiumAdapter = readBool(props, "ebeSodiumAdapter", true, modified);
        ebeVitrailAdapter = readBool(props, "ebeVitrailAdapter", true, modified);
        moreCulling = readBool(props, "moreCulling", true, modified);
        guiBatching = readBool(props, "guiBatching", true, modified);
        badOptimizations = readBool(props, "badOptimizations", true, modified);
        alternateCurrent = readBool(props, "alternateCurrent", true, modified);
        lithiumDeep = readBool(props, "lithiumDeep", true, modified);
        noisiumC2me = readBool(props, "noisiumC2me", true, modified);
        fastFurnace = readBool(props, "fastFurnace", true, modified);
        clumps = readBool(props, "clumps", true, modified);
        serverCoreMspt = readBool(props, "serverCoreMspt", true, modified);
        ferriteCore = readBool(props, "ferriteCore", true, modified);
        modernFix = readBool(props, "modernFix", true, modified);
        krypton = readBool(props, "krypton", true, modified);

        msptTarget = readDouble(props, "msptTarget", 45.0, 10.0, 100.0, modified);
        particleLodDistance = readDouble(props, "particleLodDistance", 30.0, 12.0, 128.0, modified);

        if (modified[0]) {
            writeConfig(path);
        }
    }

    private static boolean readBool(Properties props, String key, boolean def, boolean[] modified) {
        String raw = props.getProperty(key);
        if (raw == null) {
            modified[0] = true;
            return def;
        }
        String trimmed = raw.trim();
        if ("true".equalsIgnoreCase(trimmed)) {
            return true;
        }
        if ("false".equalsIgnoreCase(trimmed)) {
            return false;
        }
        LOGGER.warn("Invalid boolean for '{}' ('{}'); falling back to default {}.", key, raw, def);
        modified[0] = true;
        return def;
    }

    private static double readDouble(Properties props, String key, double def, double min, double max, boolean[] modified) {
        String raw = props.getProperty(key);
        if (raw == null) {
            modified[0] = true;
            return def;
        }
        try {
            double val = Double.parseDouble(raw.trim());
            if (Double.isNaN(val) || Double.isInfinite(val) || val < min || val > max) {
                LOGGER.warn("Out-of-range value for '{}' ('{}', expected {}..{}); falling back to {}.", key, raw, min, max, def);
                modified[0] = true;
                return def;
            }
            return val;
        } catch (NumberFormatException ignored) {
            LOGGER.warn("Invalid number for '{}' ('{}'); falling back to {}.", key, raw, def);
            modified[0] = true;
            return def;
        }
    }

    private static void writeConfig(Path path) {
        Map<String, String> ordered = new LinkedHashMap<>();
        ordered.put("entityCulling", Boolean.toString(entityCulling));
        ordered.put("particleCulling", Boolean.toString(particleCulling));
        ordered.put("ebe", Boolean.toString(ebe));
        ordered.put("ebeSodiumAdapter", Boolean.toString(ebeSodiumAdapter));
        ordered.put("ebeVitrailAdapter", Boolean.toString(ebeVitrailAdapter));
        ordered.put("moreCulling", Boolean.toString(moreCulling));
        ordered.put("guiBatching", Boolean.toString(guiBatching));
        ordered.put("badOptimizations", Boolean.toString(badOptimizations));
        ordered.put("alternateCurrent", Boolean.toString(alternateCurrent));
        ordered.put("lithiumDeep", Boolean.toString(lithiumDeep));
        ordered.put("noisiumC2me", Boolean.toString(noisiumC2me));
        ordered.put("fastFurnace", Boolean.toString(fastFurnace));
        ordered.put("clumps", Boolean.toString(clumps));
        ordered.put("serverCoreMspt", Boolean.toString(serverCoreMspt));
        ordered.put("ferriteCore", Boolean.toString(ferriteCore));
        ordered.put("modernFix", Boolean.toString(modernFix));
        ordered.put("krypton", Boolean.toString(krypton));
        ordered.put("msptTarget", Double.toString(msptTarget));
        ordered.put("particleLodDistance", Double.toString(particleLodDistance));

        StringBuilder sb = new StringBuilder(512);
        sb.append("# Gluon module configuration\n");
        for (Map.Entry<String, String> e : ordered.entrySet()) {
            sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        }
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException t) {
            LOGGER.warn("Unable to write config file {}.", path, t);
        }
    }

    public static boolean entityCulling() {
        ensureLoaded();
        return entityCulling;
    }

    public static boolean particleCulling() {
        ensureLoaded();
        return particleCulling;
    }

    public static boolean ebe() {
        ensureLoaded();
        return ebe;
    }

    public static boolean ebeSodiumAdapter() {
        ensureLoaded();
        return ebeSodiumAdapter;
    }

    public static boolean ebeVitrailAdapter() {
        ensureLoaded();
        return ebeVitrailAdapter;
    }

    public static boolean moreCulling() {
        ensureLoaded();
        return moreCulling;
    }

    public static boolean guiBatching() {
        ensureLoaded();
        return guiBatching;
    }

    public static boolean badOptimizations() {
        ensureLoaded();
        return badOptimizations;
    }

    public static boolean alternateCurrent() {
        ensureLoaded();
        return alternateCurrent;
    }

    public static boolean lithiumDeep() {
        ensureLoaded();
        return lithiumDeep;
    }

    public static boolean noisiumC2me() {
        ensureLoaded();
        return noisiumC2me;
    }

    public static boolean fastFurnace() {
        ensureLoaded();
        return fastFurnace;
    }

    public static boolean clumps() {
        ensureLoaded();
        return clumps;
    }

    public static boolean serverCoreMspt() {
        ensureLoaded();
        return serverCoreMspt;
    }

    public static boolean ferriteCore() {
        ensureLoaded();
        return ferriteCore;
    }

    public static boolean modernFix() {
        ensureLoaded();
        return modernFix;
    }

    public static boolean krypton() {
        ensureLoaded();
        return krypton;
    }

    public static double msptTarget() {
        ensureLoaded();
        return msptTarget;
    }

    public static double particleLodDistance() {
        ensureLoaded();
        return particleLodDistance;
    }

    public static List<String> getDisabledGroups() {
        ensureLoaded();
        List<String> list = new ArrayList<>();
        if (!entityCulling) list.add("entityCulling");
        if (!particleCulling) list.add("particleCulling");
        if (!ebe) list.add("ebe");
        if (!ebeSodiumAdapter) list.add("ebeSodiumAdapter");
        if (!ebeVitrailAdapter) list.add("ebeVitrailAdapter");
        if (!moreCulling) list.add("moreCulling");
        if (!guiBatching) list.add("guiBatching");
        if (!badOptimizations) list.add("badOptimizations");
        if (!alternateCurrent) list.add("alternateCurrent");
        if (!lithiumDeep) list.add("lithiumDeep");
        if (!noisiumC2me) list.add("noisiumC2me");
        if (!fastFurnace) list.add("fastFurnace");
        if (!clumps) list.add("clumps");
        if (!serverCoreMspt) list.add("serverCoreMspt");
        if (!ferriteCore) list.add("ferriteCore");
        if (!modernFix) list.add("modernFix");
        if (!krypton) list.add("krypton");
        return Collections.unmodifiableList(list);
    }
}
