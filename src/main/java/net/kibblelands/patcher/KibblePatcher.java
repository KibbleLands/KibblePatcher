package net.kibblelands.patcher;

import net.kibblelands.patcher.ext.ForEachRemover;
import net.kibblelands.patcher.patches.*;
import net.kibblelands.patcher.rebuild.ClassDataProvider;
import net.kibblelands.patcher.serverclip.ServerClipSupport;
import net.kibblelands.patcher.utils.*;
import net.kibblelands.patcher.utils.logger.Logger;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static net.kibblelands.patcher.utils.ASMUtils.ASM_BUILD;

public class KibblePatcher implements Opcodes {
    private final Logger logger;

    public KibblePatcher(Logger logger) {
        this.logger = logger;
    }

    private static final String CRAFT_BUKKIT_MAIN = "org/bukkit/craftbukkit/Main.class";
    private static final String BUKKIT_API = "org/bukkit/Bukkit.class";
    private static final String BUKKIT_VERSION_COMMAND = "org/bukkit/command/defaults/VersionCommand.class";
    private static final String PAPER_JVM_CHECKER_OLD = "com/destroystokyo/paper/util/PaperJvmChecker.class";
    private static final String PAPER_JVM_CHECKER = "io/papermc/paper/util/PaperJvmChecker.class";
    private static final CancellationException SKIP = new CancellationException();
    public static final String KIBBLE_VERSION = "1.6.3";
    // Enable dev warnings if the version contains "-dev"
    @SuppressWarnings("ALL")
    public static final boolean DEV_BUILD = KIBBLE_VERSION.contains("-dev");
    /**
     * KillSwitch for compatibility patches
     * Can be disabled if your server doesn't require it
     */
    public boolean compatibilityPatches = true;
    /**
     * KillSwitch for external patches
     * Can be disabled to removes patch that fall under an unclear licence
     */
    public boolean externalPatches = true;
    /**
     * KillSwitch for features patches
     * Can be disabled if your server doesn't require it
     */
    public boolean featuresPatches = false;
    /**
     * Special mode for servers build process
     * to only include KibblePatcher optimisations
     */
    public boolean builtInMode = false;
    /**
     * Flag to tell if
     */
    public boolean builtInModeRewrite = false;
    /**
     * Prefix of FastMath and FastReplace classes
     */
    public String builtInPkg = "net/kibblelands/server/util/";

    public void patchServerJar(File in, File out) throws IOException {
        if (DEV_BUILD) {
            logger.warn("This version is an unofficial dev build of KibblePatcher!");
        }
        ServerClipSupport serverClipSupport = ServerClipSupport.getServerClipSupport(in);
        if (serverClipSupport != null) {
            logger.info("Generating " + serverClipSupport.getName().toLowerCase() + " server...");
            this.patchServerJar0(serverClipSupport.patchServerClip(in), out);
            ServerClipSupport.cleanServerClip();
        } else {
           this.patchServerJar0(in, out);
        }
    }

    private void patchServerJar0(File in, File out) throws IOException {
        final boolean special = ((!this.featuresPatches) || (!this.compatibilityPatches) || this.builtInMode);
        logger.info("Reading jar..."); //////////////////////////////////////////////////////////////
        byte[] origBytes = Files.readAllBytes(in.toPath());
        boolean javaZipMitigation = false;
        boolean libraryMode = false;
        if (origBytes[0x06] == 0x00 && origBytes[0x07] == 0x00 && origBytes[0x08] == 0x08) {
            origBytes[0x07] = 0x08;
            javaZipMitigation = true;
        }
        Map<String, byte[]> orig = IOUtils.readZIP(new ByteArrayInputStream(origBytes));
        Map<String, byte[]> patch = new HashMap<>();
        Map<String, byte[]> inject = new HashMap<>();
        Map<String, byte[]> srv = new PatchMap<>(orig, patch);
        if (srv.get(JarFile.MANIFEST_NAME) == null) {
            logger.error("The file is not a valid java archive!");
            System.exit(4);
            return;
        }
        Manifest manifest = new Manifest(new ByteArrayInputStream(srv.get(JarFile.MANIFEST_NAME)));
        if (manifest.getMainAttributes().containsKey("Kibble-Version") ||
                srv.containsKey("net/kibblelands/server/util/FastMath.class")) {
            String kibbleVer = manifest.getMainAttributes().getValue("Kibble-Version");
            logger.error("The file was already patched by Kibble "+kibbleVer+"!");
            logger.warn("Please use original server file for patching!");
            System.exit(4);
            return;
        }
        // This help plugins to prevent their behaviour from changing if KibblePatcher lite is present
        // As if a plugin made for KibblePatcher detect it it might use APIs not available in the partial
        // version, the dynamic package is also here to discourage the use of these APIs in lite mode
        boolean isBuiltInPatched = (!this.builtInMode) &&
                manifest.getMainAttributes().containsKey("Kibble-BuiltIn");
        boolean isRewriteInstalled = (!this.builtInMode) && (
                manifest.getMainAttributes().containsKey("Kibble-Rewrite") ? (
                        "BUILT-IN".equals(manifest.getMainAttributes().getValue("Kibble-Rewrite"))
                ) : isBuiltInPatched);
        String StringUtil = null;
        if (srv.containsKey("org/apache/commons/lang/StringUtils.class")) {
            StringUtil = "org/apache/commons/lang/StringUtils";
        } else if (srv.containsKey("org/apache/commons/lang3/StringUtils.class")) {
            StringUtil = "org/apache/commons/lang3/StringUtils";
        } else if (srv.containsKey("org/bukkit/craftbukkit/libs/org/apache/commons/lang/StringUtils.class")) {
            StringUtil = "org/bukkit/craftbukkit/libs/org/apache/commons/lang/StringUtils";
        } else if (srv.containsKey("org/bukkit/craftbukkit/libs/org/apache/commons/lang3/StringUtils.class")) {
            StringUtil = "org/bukkit/craftbukkit/libs/org/apache/commons/lang3/StringUtils";
        }
        String MathHelper = null;
        for (String entry:srv.keySet()) {
            if (entry.startsWith("net/minecraft/server/") && entry.endsWith("/MathHelper.class")) {
                MathHelper = entry.substring(0, entry.length() - 6);
                break;
            }
        }
        String fast_util_prefix = null;
        if (srv.containsKey("it/unimi/dsi/fastutil/objects/Object2BooleanOpenHashMap.class")) {
            fast_util_prefix = "it/unimi/dsi/fastutil/";
        } else if (srv.containsKey(
                "org/bukkit/craftbukkit/libs/it/unimi/dsi/fastutil/objects/Object2BooleanOpenHashMap.class")) {
            fast_util_prefix = "org/bukkit/craftbukkit/libs/it/unimi/dsi/fastutil/";
        }
        if (compatibilityPatches && !javaZipMitigation && MathHelper == null
                && srv.get(CRAFT_BUKKIT_MAIN) == null && srv.get(BUKKIT_API) != null) {
            if (this.builtInMode) {
                throw new Error("BuiltIn mode is incompatible with library patching!");
            }
            logger.warn("API Patching mode! (Optimisations disabled)");
            logger.warn("Warning: API Patching mode is not officially supported!");
            libraryMode = true;
        } else if (MathHelper == null || srv.get(CRAFT_BUKKIT_MAIN) == null) {
            logger.error("Server is not a valid spigot server!");
            if (MathHelper == null) {
                logger.error("MathHelper not found!");
            }
            if (srv.get(CRAFT_BUKKIT_MAIN) == null) {
                logger.error("CraftBukkit Main class not found!");
            }
            System.exit(3);
            return;
        }
        String NMS = libraryMode ? null : MathHelper.substring(21, MathHelper.lastIndexOf('/'));
        Random accessPkgRnd = new Random();
        String accessPkg = libraryMode ? "net/kibblelands/server/private/" :
                "org/bukkit/craftbukkit/libs/" + "abcdef".charAt(accessPkgRnd.nextInt(6)) +
                        UUID.randomUUID().toString().replace("-", "").substring(0, accessPkgRnd.nextInt(32)) + "/";
        // CommonGenerator is only available if features patches are enabled
        CommonGenerator commonGenerator = new CommonGenerator(NMS, this.featuresPatches, this.builtInMode);
        ClassDataProvider classDataProvider = new ClassDataProvider(KibblePatcher.class.getClassLoader());
        classDataProvider.addClasses(srv);
        System.gc(); // Clean memory
        logger.info("Pawtching jar..."); //////////////////////////////////////////////////////////////
        int[] stats = {0, 0, 0, 0, 0, 0, 0};
        // Patch Manifest
        manifest.getMainAttributes().putValue(this.builtInMode ? "Kibble-BuiltIn" : "Kibble-Version", KIBBLE_VERSION);
        final boolean[] plRewrite = new boolean[]{false};
        if (this.builtInMode) {
            if (!libraryMode) {
                BlockDataOptimiser.patch(commonGenerator, srv, stats);
                if (this.builtInModeRewrite) {
                    PluginRewriteOptimiser.patch(commonGenerator, srv, inject, this.builtInPkg, plRewrite);
                }
                NMSAccessOptimizer.patch(commonGenerator, srv);
            }
        } else if (!libraryMode) {
            if (manifest.getAttributes("net/minecraft/server/") == null) {
                Attributes attributes = new Attributes();
                attributes.putValue("Sealed", "true");
                manifest.getEntries().put("net/minecraft/server/", attributes);
            }
            // Patch the 10/20 seconds delay to a 5 seconds delay
            srv.put(CRAFT_BUKKIT_MAIN, patchDelayer(commonGenerator, srv.get(CRAFT_BUKKIT_MAIN)));
            // Patch Server Brand / VersionCommand
            String NMS_SERVER = "net/minecraft/server/" + NMS + "/MinecraftServer.class";
            srv.put(NMS_SERVER, patchBrand(srv.get(NMS_SERVER), special));
            srv.put(BUKKIT_VERSION_COMMAND, patchVersionCmd(srv.get(BUKKIT_VERSION_COMMAND), special));
            // Add GC on reload / init to better manage RAM
            String CRAFT_SERVER = "org/bukkit/craftbukkit/" + NMS + "/CraftServer.class";
            srv.put(CRAFT_SERVER, patchGC(srv.get(CRAFT_SERVER), "reload", stats));
            String NMS_DEDICATED_SERVER = "net/minecraft/server/" + NMS + "/DedicatedServer.class";
            srv.put(NMS_DEDICATED_SERVER, patchGC(srv.get(NMS_DEDICATED_SERVER), "init", stats));
            if (this.featuresPatches) {
                byte[] paperJvmCheck = srv.get(PAPER_JVM_CHECKER);
                if (paperJvmCheck != null) {
                    srv.put(PAPER_JVM_CHECKER, patchPaperJavaWarning(paperJvmCheck));
                }
                paperJvmCheck = srv.get(PAPER_JVM_CHECKER_OLD);
                if (paperJvmCheck != null) {
                    srv.put(PAPER_JVM_CHECKER_OLD, patchPaperJavaWarning(paperJvmCheck));
                }
            }
            if (compatibilityPatches) {
                // Add commonly used APIs on old plugins
                OnlinePlayersCompact.check(commonGenerator, srv, stats);
                InventoryCompact.check(commonGenerator, srv, stats);
                EntityCompact.check(commonGenerator, srv, stats);
                PlayerPatcherCompact.check(commonGenerator, srv, stats);
                GuavaVarTypeCompact.check(commonGenerator, srv, stats);
                BukkitVarTypeCompact.check(commonGenerator, srv, stats);
            }
            // Security patches
            BookCrashFixer.patch(commonGenerator, srv, stats);
            AuthenticationHardening.patch(commonGenerator, srv, stats);
            // Specific optimisations
            if (!isBuiltInPatched) {
                ChunkCacheOptimizer.patch(commonGenerator, srv, stats);
                MethodResultCacheOptimizer.patch(commonGenerator, srv, stats);
                BlockDataOptimiser.patch(commonGenerator, srv, stats);
            }
            if (!isRewriteInstalled) {
                PluginRewriteOptimiser.patch(commonGenerator, srv, inject, accessPkg, plRewrite);
            }
            NMSAccessOptimizer.patch(commonGenerator, srv);
            // Add features patches
            if (featuresPatches) {
                DataCommandFeature.install(commonGenerator, srv, stats);
                EntityPropertiesFeature.install(commonGenerator, srv, inject, stats);
                BiomeConfigAPIFeature.install(commonGenerator, srv, inject, classDataProvider);
                DimensionConfigAPIFeature.install(commonGenerator, srv, inject, classDataProvider);
            }
            // Save in the jar if plugin rewrite is supported/installed
            manifest.getMainAttributes().putValue("Kibble-Rewrite",
                    isRewriteInstalled?"BUILT-IN":plRewrite[0]?"INSTALLED":"UNSUPPORTED");
        } else {
            // Add features in lib mode
            if (featuresPatches) {
                EntityPropertiesFeature.installLib(inject);
                BiomeConfigAPIFeature.installLib(srv, inject);
                DimensionConfigAPIFeature.installLib(srv, inject);
            }
        }
        if (compatibilityPatches) {
            // These classes are used by some plugins but are no longer available since java 9
            if (!srv.containsKey("javax/xml/bind/DatatypeConverter.class")) {
                inject.put("javax/xml/bind/DatatypeConverter.class",
                        IOUtils.readResource("javax/xml/bind/DatatypeConverter.class"));
                inject.put("javax/xml/bind/annotation/adapters/XmlAdapter.class",
                        IOUtils.readResource("javax/xml/bind/annotation/adapters/XmlAdapter.class"));
                inject.put("javax/xml/bind/annotation/adapters/HexBinaryAdapter.class",
                        IOUtils.readResource("javax/xml/bind/annotation/adapters/HexBinaryAdapter.class"));
            }
        }
        if (((!this.builtInMode) || this.builtInPkg.equals("net/kibblelands/server/util/"))
                && !(isBuiltInPatched && srv.containsKey("net/kibblelands/server/util/FastMath.class"))) {
            byte[] FastMathAPI = IOUtils.readResource("net/kibblelands/server/util/FastMath.class");
            byte[] FastReplaceAPI = IOUtils.readResource("net/kibblelands/server/util/FastReplace.class");
            byte[] FastCollectionsAPI = IOUtils.readResource("net/kibblelands/server/util/FastCollections.class");
            if (!libraryMode) { // Mirror FastMath to MathHelper
                ClassNode fastMathClassNode = new ClassNode();
                new ClassReader(FastMathAPI).accept(fastMathClassNode, 0);
                for (MethodNode methodNode : fastMathClassNode.methods) {
                    if (!methodNode.name.startsWith("<") && !methodNode.name.equals("tan")) {
                        methodNode.instructions.clear();
                        boolean d2f = methodNode.desc.indexOf('D') != -1;
                        if (d2f) {
                            methodNode.instructions.add(new VarInsnNode(DLOAD, 0));
                            methodNode.instructions.add(new InsnNode(D2F));
                        } else {
                            methodNode.instructions.add(new VarInsnNode(FLOAD, 0));
                        }
                        methodNode.instructions.add(new MethodInsnNode(INVOKESTATIC,
                                MathHelper, methodNode.name, "(F)F", false));
                        if (d2f) {
                            methodNode.instructions.add(new InsnNode(F2D));
                            methodNode.instructions.add(new InsnNode(DRETURN));
                        } else {
                            methodNode.instructions.add(new InsnNode(FRETURN));
                        }
                    }
                }
                ClassWriter classWriter = classDataProvider.newClassWriter();
                fastMathClassNode.accept(classWriter);
                FastMathAPI = classWriter.toByteArray();
                ClassNode fastReplaceClassNode = new ClassNode();
                new ClassReader(FastReplaceAPI).accept(fastReplaceClassNode, 0);
                if (StringUtil != null) {
                    for (MethodNode methodNode : fastReplaceClassNode.methods) {
                        if (!methodNode.name.startsWith("<")) {
                            if (methodNode.desc.contains("CharSequence")) {
                                for (AbstractInsnNode insnNode : methodNode.instructions) {
                                    if (insnNode.getOpcode() == INVOKESTATIC) {
                                        MethodInsnNode methodInsnNode = (MethodInsnNode) insnNode;
                                        if (methodInsnNode.name.equals("replace")) {
                                            methodInsnNode.owner = StringUtil;
                                            break;
                                        }
                                    }
                                }
                            } else {
                                methodNode.instructions.clear();
                                methodNode.instructions.add(new VarInsnNode(ALOAD, 0));
                                methodNode.instructions.add(new VarInsnNode(ALOAD, 1));
                                methodNode.instructions.add(new VarInsnNode(ALOAD, 2));
                                methodNode.instructions.add(new MethodInsnNode(INVOKESTATIC,
                                        StringUtil, "replace", methodNode.desc, false));
                                methodNode.instructions.add(new InsnNode(ARETURN));
                            }
                        }
                    }
                    classWriter = classDataProvider.newClassWriter();
                    fastReplaceClassNode.accept(classWriter);
                    FastReplaceAPI = classWriter.toByteArray();
                }
                ClassNode fastCollectionsClassNode = new ClassNode();
                if (fast_util_prefix != null) {
                    new ClassReader(FastCollectionsAPI).accept(new ClassRelocator(
                            new ClassRelocator(fastCollectionsClassNode,
                            new PrefixRemapper("java/util/HashMap",
                                    fast_util_prefix+"objects/Object2ObjectOpenHashMap")),
                            new PrefixRemapper("java/util/HashSet",
                                    fast_util_prefix+"objects/ObjectOpenHashSet")), 0);
                    classWriter = classDataProvider.newClassWriter();
                    fastCollectionsClassNode.accept(classWriter);
                    FastCollectionsAPI = classWriter.toByteArray();
                } else {
                    new ClassReader(FastCollectionsAPI).accept(fastCollectionsClassNode, 0);
                }
                if (!isBuiltInPatched) {
                    // Hide actual access methods, help against detection
                    // Some plugins no longer behave like intended
                    Remapper hideRemapper = new PrefixRemapper("net/kibblelands/server/util/", accessPkg);
                    classWriter = classDataProvider.newClassWriter();
                    fastMathClassNode.accept(new ClassRelocator(classWriter, hideRemapper));
                    inject.put(accessPkg + "FastMath.class", classWriter.toByteArray());
                    classWriter = classDataProvider.newClassWriter();
                    fastReplaceClassNode.accept(new ClassRelocator(classWriter, hideRemapper));
                    inject.put(accessPkg + "FastReplace.class", classWriter.toByteArray());
                    classWriter = classDataProvider.newClassWriter();
                    fastCollectionsClassNode.accept(new ClassRelocator(classWriter, hideRemapper));
                    inject.put(accessPkg + "FastCollections.class", classWriter.toByteArray());
                }
            }
            if (this.featuresPatches) {
                inject.put("net/kibblelands/server/util/FastMath.class", FastMathAPI);
                inject.put("net/kibblelands/server/util/FastReplace.class", FastReplaceAPI);
                inject.put("net/kibblelands/server/util/FastCollections.class", FastCollectionsAPI);
                commonGenerator.addChangeEntry("Added FastAPI. " + ConsoleColors.CYAN + "(Feature)");
            }
        }
        commonGenerator.generate(inject, srv);
        // Optimise all classes
        srv.values().removeIf(Objects::isNull); // Clean null elements
        if (!libraryMode) {
            if (!isBuiltInPatched) {
                Iterator<Map.Entry<String, byte[]>> iterator = srv.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<String, byte[]> entry = iterator.next();
                    try {
                        if (entry.getKey().endsWith(".class")) {
                            patchClassOpt(entry, classDataProvider, fast_util_prefix == null ||
                                            entry.getKey().startsWith(fast_util_prefix) ? null : fast_util_prefix,
                                    entry.getKey().startsWith("org/apache/commons/math3/") ||
                                            entry.getKey().startsWith("net/minecraft/server/")
                                            ? null : MathHelper, stats, accessPkg);
                        } else if (entry.getKey().equals("pack.mcmeta") || entry.getKey().endsWith(".json")) {
                            entry.setValue(IOUtils.trimJSON(new String(entry.getValue(),
                                    StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8));
                        }
                    } catch (CancellationException c) {
                        if (c != SKIP) throw c;
                        iterator.remove();
                    }
                }
            }
            if (!this.builtInMode) {
                // Patch default config for performance
                if (srv.get("configurations/bukkit.yml") != null) {
                    srv.put("configurations/bukkit.yml", new String(srv.get("configurations/bukkit.yml"), StandardCharsets.UTF_8)
                            .replace("query-plugins: true", "query-plugins: false")
                            .replace("monster-spawns: 1", "monster-spawns: 2").replace("water-spawns: 1", "water-spawns: 2")
                            .replace("ambient-spawns: 1", "ambient-spawns: 2").getBytes(StandardCharsets.UTF_8));
                }
            }
            // Get stats in final jar
            manifest.getMainAttributes().putValue(this.builtInMode ? "Kibble-BuiltIn-Stats" : "Kibble-Stats", Arrays.toString(stats));
        }
        orig = null;
        srv = null;
        System.gc(); // Clean memory
        logger.info("Building jar..."); //////////////////////////////////////////////////////////////
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        patchZIP(new ByteArrayInputStream(origBytes), byteArrayOutputStream, manifest, patch, inject);
        origBytes = byteArrayOutputStream.toByteArray();
        byteArrayOutputStream = null;
        patch = null;
        if (javaZipMitigation) {
            origBytes[0x06] = 0x00;
            origBytes[0x07] = 0x00;
            origBytes[0x08] = 0x08;
        }
        System.gc(); // Clean memory
        logger.info("Writing jar..."); //////////////////////////////////////////////////////////////
        FileOutputStream fileOutputStream = new FileOutputStream(out);
        fileOutputStream.write(origBytes);
        fileOutputStream.flush();
        fileOutputStream.close();
        logger.info("Finished!\n");
        if (!this.builtInMode) {
            commonGenerator.printChanges(logger);
            logger.info("Server patcher: ");
            logger.info("  Plugin rewrite: "+(isBuiltInPatched?
                    (ConsoleColors.CYAN + "BUILT-IN"):plRewrite[0]?
                    (ConsoleColors.CYAN + "INSTALLED"):
                    (ConsoleColors.YELLOW + "UNSUPPORTED")));
        }
        if (isBuiltInPatched) {
            logger.info("Generic optimiser: " + ConsoleColors.YELLOW + "Skipped");
        } else {
            logger.info("Generic optimiser: ");
            logger.info("  Optimised java calls: " + ConsoleColors.CYAN + stats[1]);
            logger.info("  Optimised opcodes: " + ConsoleColors.CYAN + stats[2]);
            if (externalPatches) {
                logger.info("  Optimised forEach: " + ConsoleColors.CYAN + stats[6]);
            }
        }
        System.out.println();
        printSupportLinks(logger);
    }

    public void patchZIP(final InputStream in, final OutputStream out, Manifest manifest, Map<String, byte[]> patch, Map<String, byte[]> inject) throws IOException {
        ZipInputStream inputStream = new ZipInputStream(in);
        KibbleOutputStream zip = new KibbleOutputStream(out, manifest);
        zip.setComment("Patched by Kibble "+KIBBLE_VERSION.replace('_', ' '));
        ZipEntry entry;
        byte[] buffer = new byte[2048];
        while (null!=(entry=inputStream.getNextEntry())) {
            if (entry.getName().equals(JarFile.MANIFEST_NAME) || (entry.getName().startsWith("javax/annotation/") && entry.getName().endsWith(".java"))) {
                continue;
            }
            // Yatopia can have duplicate zip entries
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try {
                // We need to check actual size of entries, because directories can contain data
                if (entry.isDirectory() && entry.getSize() == 0) {
                    zip.putNextEntry(entry); // Store directories as-is
                } else {
                    // Support data directories
                    byte[] data = entry.isDirectory() ? null : patch.get(entry.getName());
                    if (data != null) {
                        // We can't reuse a ZipEntry instance if the entry isn't fully read
                        entry = new ZipEntry(entry);
                    } else {
                        baos.reset();
                        int nRead;
                        while ((nRead = inputStream.read(buffer, 0, buffer.length)) != -1) {
                            baos.write(buffer, 0, nRead);
                        }
                        data = baos.toByteArray();
                    }

                    entry.setMethod(ZipOutputStream.DEFLATED);
                    entry.setCompressedSize(-1);
                    entry.setSize(data.length);
                    zip.putNextEntry(entry);
                    zip.write(data);
                }
                zip.closeEntry();
            } catch (ZipException zipException) {
                if (!zipException.getMessage().startsWith("duplicate entry: ")) {
                    throw zipException;
                }
                this.logger.warn("Deduplicated zip entry: " + entry.getName());
            }
        }
        for (Map.Entry<String, byte[]> toInject:inject.entrySet()) {
            entry = new ZipEntry(toInject.getKey());
            entry.setMethod(ZipOutputStream.DEFLATED);
            zip.putNextEntry(entry);
            zip.write(toInject.getValue());
            zip.closeEntry();
        }
        zip.flush();
        zip.close();
        in.close();
    }

    public static byte[] patchDelayer(CommonGenerator commonGenerator, byte[] bytes) {
        final boolean[] didWork = new boolean[]{false};
        ClassReader classReader = new ClassReader(bytes);
        ClassWriter classWriter = new ClassWriter(0);
        classReader.accept(new ClassVisitor(ASM_BUILD, classWriter) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(ASM_BUILD, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    boolean patch20 = false;
                    boolean patch10 = false;

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String) {
                            if (((String) value).contains("will start in 20 seconds")) {
                                patch20 = true;
                                value = ((String) value).replace("20", "5");
                            } else if (((String) value).equalsIgnoreCase("Starting server in 10 seconds")) {
                                patch10 = true;
                                value = ((String) value).replace("10", "5");
                            }
                        } else if (value instanceof Long) {
                            if (patch20 && (Long) value == 20L) {
                                patch20 = false;
                                didWork[0] = true;
                                value = 5L;
                            } else if (patch10 && (Long) value == 10L) {
                                patch10 = false;
                                didWork[0] = true;
                                value = 5L;
                            }
                        }
                        super.visitLdcInsn(value);
                    }
                };
            }
        }, 0);
        if (didWork[0]) {
            commonGenerator.addChangeEntry("Reduced start delay when outdated to 5 seconds. " + ConsoleColors.CYAN + "(Productivity)");
        }
        return didWork[0] ? classWriter.toByteArray() : bytes;
    }

    public static byte[] patchPaperJavaWarning(byte[] bytes) {
        ClassReader classReader = new ClassReader(bytes);
        ClassNode classNode = new ClassNode();
        classReader.accept(classNode, 0);
        for (MethodNode methodNode:classNode.methods) {
            if ((methodNode.name.equals("printWarning") ||
                    methodNode.name.equals("checkJvm")) &&
                    methodNode.desc.endsWith(")V")) {
                methodNode.instructions.clear();
                methodNode.instructions.add(new InsnNode(RETURN));
                methodNode.tryCatchBlocks.clear();
                methodNode.localVariables.clear();
            }
        }
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(classWriter);
        return classWriter.toByteArray();
    }

    public static byte[] patchBrand(byte[] bytes,final boolean special) {
        if (bytes == null) return null;
        ClassReader classReader = new ClassReader(bytes);
        ClassWriter classWriter = new ClassWriter(0);
        classReader.accept(new ClassVisitor(ASM_BUILD, classWriter) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (!name.equals("getServerModName")) {
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
                return new MethodVisitor(ASM_BUILD, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String) {
                            String srv;
                            switch ((String) value) {
                                case "cb":
                                    value = "CraftBukkit";
                                default:
                                    srv = value + "/Kibble";
                                    break;
                                case "Spigot":
                                    srv = "KibbleSpigot";
                                    break;
                                case "Paper":
                                    srv = "KibblePaper";
                                    break;
                            }
                            if (special) {
                                srv += "*";
                            }
                            value = "\u00A7b" + srv + "\u00A7r";
                        }
                        super.visitLdcInsn(value);
                    }
                };
            }
        }, 0);
        return classWriter.toByteArray();
    }

    public static byte[] patchVersionCmd(byte[] bytes,final boolean special) {
        if (bytes == null) return null;
        ClassReader classReader = new ClassReader(bytes);
        ClassWriter classWriter = new ClassWriter(0);
        classReader.accept(new ClassVisitor(ASM_BUILD, classWriter) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(ASM_BUILD, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String) {
                            String ldc = (String) value;
                            if (ldc.contains("Implementing API version")) {
                                value = " \u00A7bKibble " + KIBBLE_VERSION + (special ? "*": "") + "\u00A7r" + (ldc.charAt(0) == ' ' ? "" : " ") + ldc;
                            }
                        }
                        super.visitLdcInsn(value);
                    }
                };
            }
        }, 0);
        return classWriter.toByteArray();
    }

    public static byte[] patchGC(byte[] bytes,String method,final int[] stats) {
        if (bytes == null) return null;
        ClassReader classReader = new ClassReader(bytes);
        ClassWriter classWriter = new ClassWriter(0);
        classReader.accept(new ClassVisitor(ASM_BUILD, classWriter) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (!name.equals(method)) {
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
                return new MethodVisitor(ASM_BUILD, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode == RETURN || opcode == IRETURN) {
                            super.visitMethodInsn(INVOKESTATIC, "java/lang/System", "gc", "()V", false);
                            stats[4]++;
                        }
                        super.visitInsn(opcode);
                    }
                };
            }
        }, 0);
        return classWriter.toByteArray();
    }

    private static final int FIRST_PASS = 0;
    private static final int FATAL_ERROR = 1;
    private static final int NON_FATAL_ERROR = 2;

    public void patchClassOpt(Map.Entry<String, byte[]> p,ClassDataProvider cdp, String fast_util_prefix,
                              String Math,final int[] stats,String accessPkg) throws IOException {
        patchClassOpt(p, cdp,fast_util_prefix, Math, stats, FIRST_PASS, accessPkg);
    }

    private static final String replaceDesc =
            "(Ljava/lang/String;Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;";

    private void patchClassOpt(final Map.Entry<String, byte[]> p,ClassDataProvider cdp, String fast_util_prefix,
                               String Math, final int[] stats,final int err,final String accessPkg) throws IOException {
        boolean[] requireCalc_dontOptimise = new boolean[]{false, false};
        ClassReader classReader = new ClassReader(p.getValue());
        ClassNode classNode = new ClassNode();
        classReader.accept(classNode, ClassReader.SKIP_FRAMES);
        if (externalPatches) {
            ForEachRemover.transform(classNode, cdp, stats);
        }
        ClassWriter classWriter = new ClassWriter(0);

        classNode.accept(new ClassVisitor(ASM_BUILD, classWriter) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                super.visit(version, access, name, signature, superName, interfaces);
                requireCalc_dontOptimise[1] = (version < V1_6 && !name.startsWith("net/minecraft/server/")
                        && !(name.startsWith("org/bukkit/") && !name.startsWith("org/bukkit/craftbukkit/libs/")));
            }

            @Override
            public MethodVisitor visitMethod(int access, final String m_name, final String m_descriptor, String signature, String[] exceptions) {
                if (requireCalc_dontOptimise[1]) return new MethodVisitor(ASM_BUILD) {};
                final MethodVisitor parentMethodVisitor = super.visitMethod(access, m_name, m_descriptor, signature, exceptions);
                return new MethodVisitor(ASM_BUILD, new MethodNode(access, m_name, m_descriptor, signature, exceptions)) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                        if (opcode == INVOKESTATIC && (owner.equals("java/lang/Math") || owner.equals("java/lang/StrictMath"))) {
                            if (Math != null && (name.equals("sin") || name.equals("cos") || name.equals("tan"))) {
                                owner = Math;
                                stats[1]++;
                                if (descriptor.endsWith(")D")) {
                                    super.visitInsn(D2F);
                                    super.visitMethodInsn(opcode, owner, name, descriptor.replace('D', 'F'), isInterface);
                                    super.visitInsn(F2D);
                                    requireCalc_dontOptimise[0] = true;
                                    return;
                                }
                            }
                            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                            return;
                        } else if (opcode == INVOKEVIRTUAL && owner.equals("java/lang/String") &&
                                name.equals("replace") && descriptor.contains("CharSequence")) {
                            opcode = INVOKESTATIC;
                            owner = KibblePatcher.this.builtInMode ?
                                    (KibblePatcher.this.builtInPkg + "FastReplace") :
                                    accessPkg + "FastReplace";
                            descriptor = replaceDesc;
                            stats[1]++;
                        }
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                    }

                    @Override
                    public void visitEnd() {
                        InsnList insnNodes = ((MethodNode) this.mv).instructions;
                        for (AbstractInsnNode insnNode : insnNodes.toArray()) {
                            AbstractInsnNode previous = insnNode.getPrevious();
                            MethodInsnNode methodInsnNode;
                            if (previous == null) continue;
                            switch (insnNode.getOpcode()) {
                                case F2D:
                                    if (previous.getOpcode() == D2F) {
                                        insnNodes.remove(previous);
                                        insnNodes.remove(insnNode);
                                    }
                                    break;
                                case D2F:
                                    if (previous.getOpcode() == F2D) {
                                        insnNodes.remove(previous);
                                        insnNodes.remove(insnNode);
                                    }
                                    break;
                                case POP:
                                    if (previous.getOpcode() == DUP) {
                                        insnNodes.remove(previous);
                                        insnNodes.remove(insnNode);
                                    }
                                    break;
                                case POP2:
                                    if (previous.getOpcode() == DUP2) {
                                        insnNodes.remove(previous);
                                        insnNodes.remove(insnNode);
                                    }
                                    break;
                                case INVOKESPECIAL:
                                    methodInsnNode = (MethodInsnNode) insnNode;
                                    if (methodInsnNode.desc.equals("()V") &&
                                            previous.getOpcode() == DUP) {
                                        AbstractInsnNode next = insnNode.getNext();
                                        if (next != null && next.getOpcode() == POP) {
                                            insnNodes.remove(previous);
                                            insnNodes.remove(next);
                                            stats[2]++;
                                        }
                                    }
                                    break;
                                case INVOKEINTERFACE:
                                    if (fast_util_prefix != null) {
                                        methodInsnNode = (MethodInsnNode) insnNode;
                                        if (previous.getOpcode() == INVOKEINTERFACE && methodInsnNode.name.equals("iterator")
                                                && methodInsnNode.owner.equals(fast_util_prefix + "objects/ObjectSet")) {
                                            methodInsnNode = (MethodInsnNode) previous;
                                            if (methodInsnNode.owner.startsWith(fast_util_prefix) &&
                                                    methodInsnNode.name.endsWith("2ObjectEntrySet")) {
                                                String switchIdentifier = methodInsnNode.owner.substring(
                                                        fast_util_prefix.length()) + "." + methodInsnNode.name;
                                                String utilsCl = null;
                                                switch (switchIdentifier) {
                                                    case "shorts/Short2ObjectMap.short2ObjectEntrySet": {
                                                        utilsCl = fast_util_prefix + "ints/Short2ObjectMaps";
                                                        break;
                                                    }
                                                    case "ints/Int2ObjectMap.int2ObjectEntrySet": {
                                                        utilsCl = fast_util_prefix + "ints/Int2ObjectMaps";
                                                        break;
                                                    }
                                                    case "longs/Long2ObjectMap.long2ObjectEntrySet": {
                                                        utilsCl = fast_util_prefix + "longs/Long2ObjectMaps";
                                                        break;
                                                    }
                                                    case "objects/Object2ObjectMap.object2ObjectEntrySet": {
                                                        utilsCl = fast_util_prefix + "objects/Object2ObjectMaps";
                                                        break;
                                                    }
                                                }
                                                if (utilsCl != null) {
                                                    insnNodes.remove(insnNode);
                                                    methodInsnNode.setOpcode(INVOKESTATIC);
                                                    methodInsnNode.desc = "(L" + methodInsnNode.owner + ";)L" +
                                                            fast_util_prefix + "objects/ObjectIterator;";
                                                    methodInsnNode.owner = utilsCl;
                                                    methodInsnNode.name = "fastIterator";
                                                    methodInsnNode.itf = false;
                                                    stats[1]++;
                                                }
                                            }
                                        }
                                    }
                            }
                        }
                        ((MethodNode) this.mv).accept(parentMethodVisitor);
                    }
                };
            }
        });

        if (err != FIRST_PASS) {
            if (err == NON_FATAL_ERROR) {
                Files.write(new File("malformed.nf.class").toPath(), classWriter.toByteArray());
                this.logger.warn("Extracted 'malformed.nf.class', please send your server-jar alongside with the generated file.");
            } else {
                Files.write(new File("malformed.class").toPath(), classWriter.toByteArray());
                this.logger.error("Extracted 'malformed.class', please send your server-jar alongside with the generated file.");
            }
        } else {
            if (requireCalc_dontOptimise[1]) {
                return;
            }
            byte[] data = classWriter.toByteArray();
            classWriter = cdp.newClassWriter();
            new ClassReader(data).accept(new ClassVisitor(ASM_BUILD, classWriter) {
                @Override
                public MethodVisitor visitMethod(int access, String m_name, String m_descriptor, String signature, String[] exceptions) {
                    return new MethodVisitor(ASM_BUILD, super.visitMethod(access, m_name, m_descriptor, signature, exceptions)) {
                        @Override
                        public void visitMaxs(int maxStack, int maxLocals) {
                            try {
                                super.visitMaxs(maxStack, maxLocals);
                            } catch (Throwable t) {
                                boolean fatal = !(m_name.startsWith("junit/") || m_name.startsWith("com/mysql/"));
                                try {
                                    patchClassOpt(p, cdp, fast_util_prefix, Math, stats, fatal ? FATAL_ERROR : NON_FATAL_ERROR, accessPkg);
                                } catch (IOException ignored) {}
                                if (fatal) {
                                    throw new RuntimeException("Malformed method at " + p.getKey() + "#" + m_name + m_descriptor, t);
                                } else {
                                    logger.warn("Malformed method at " + p.getKey() + "#" + m_name + m_descriptor);
                                    throw SKIP;
                                }
                            }
                        }
                    };
                }
            }, 0);
            p.setValue(classWriter.toByteArray());
        }
    }

    public Logger getLogger() {
        return logger;
    }

    public static final String GITHUB_CREATE_ISSUE = "https://github.com/KibbleLands/KibblePatcher/issues/new/choose";
    public static final String DISCORD_JOIN_LINK = "https://discord.gg/qgk4Saq";

    public static void printSupportLinks(Logger logger) {
        logger.info("If you find any bug report them here ->\n" +
                ConsoleColors.CYAN + "    " + GITHUB_CREATE_ISSUE);
        logger.info("Or you join our Discord server here ->\n" +
                ConsoleColors.CYAN + "    " + DISCORD_JOIN_LINK);
    }
}
