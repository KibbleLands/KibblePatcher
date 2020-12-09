package net.kibblelands.patcher;

import net.kibblelands.patcher.ext.ForEachRemover;
import net.kibblelands.patcher.patches.*;
import net.kibblelands.patcher.rebuild.ClassDataProvider;
import net.kibblelands.patcher.utils.ConsoleColors;
import net.kibblelands.patcher.utils.KibbleOutputStream;
import net.kibblelands.patcher.utils.PatchMap;
import net.kibblelands.patcher.utils.logger.Logger;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
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
    private static final String KIBBLE_VERSION = "1.3";
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
    public boolean featuresPatches = true;
    /**
     * Special mode for the YatopiaMC build process
     * https://github.com/YatopiaMC/Yatopia
     */
    public boolean yatopiaMode = false;

    public void patchServerJar(File in, File out) throws IOException {
        String paperVer = PaperClipSupport.getPaperClipMCVer(in);
        if (paperVer != null) {
            logger.info("Generating paperclip server...");
            this.patchServerJar0(PaperClipSupport.patchPaperClip(in, paperVer), out);
            PaperClipSupport.cleanPaperClip();
        } else {
           this.patchServerJar0(in, out);
        }
    }

    private void patchServerJar0(File in, File out) throws IOException {
        logger.info("Reading jar..."); //////////////////////////////////////////////////////////////
        byte[] origBytes = Files.readAllBytes(in.toPath());
        boolean javaZipMitigation = false;
        boolean libraryMode = false;
        if (origBytes[0x06] == 0x00 && origBytes[0x07] == 0x00 && origBytes[0x08] == 0x08) {
            origBytes[0x07] = 0x08;
            javaZipMitigation = true;
        }
        Map<String, byte[]> orig = readZIP(new ByteArrayInputStream(origBytes));
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
                srv.containsKey("net/kibblelands/server/FastMath.class")) {
            String kibbleVer = manifest.getMainAttributes().getValue("Kibble-Version");
            logger.error("The file was already patched by Kibble "+kibbleVer+"!");
            logger.warn("Please use original server file for patching!");
            System.exit(4);
            return;
        }
        boolean isYatopiaPatched = (!this.yatopiaMode) &&
                manifest.getMainAttributes().containsKey("Kibble-Yatopia");
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
        if (compatibilityPatches && !javaZipMitigation && MathHelper == null
                && srv.get(CRAFT_BUKKIT_MAIN) == null && srv.get(BUKKIT_API) != null) {
            if (this.yatopiaMode) {
                throw new Error("Yatopia mode is incompatible with library patching!");
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
        ClassDataProvider classDataProvider = new ClassDataProvider(KibblePatcher.class.getClassLoader());
        classDataProvider.addClasses(srv);
        System.gc(); // Clean memory
        logger.info("Pawtching jar..."); //////////////////////////////////////////////////////////////
        int[] stats = {0, 0, 0, 0, 0, 0, 0};
        // Patch Manifest
        manifest.getMainAttributes().putValue(this.yatopiaMode ? "Kibble-Yatopia" : "Kibble-Version", KIBBLE_VERSION);
        final boolean[] plRewrite = new boolean[]{false};
        if (this.yatopiaMode) {
            if (!libraryMode) {
                BlockDataOptimiser.patch(srv, MathHelper, stats);
                NMSAccessOptimizer.patch(srv);
            }
        } else if (!libraryMode) {
            if (manifest.getAttributes("net/minecraft/server/") == null) {
                Attributes attributes = new Attributes();
                attributes.putValue("Sealed", "true");
                manifest.getEntries().put("net/minecraft/server/", attributes);
            }
            // Patch 20 seconds delay to 5 seconds delay
            srv.put(CRAFT_BUKKIT_MAIN, patchDelayer(srv.get(CRAFT_BUKKIT_MAIN)));
            // Patch Server Brand / VersionCommand
            String NMS_SERVER = "net/minecraft/server/" + NMS + "/MinecraftServer.class";
            srv.put(NMS_SERVER, patchBrand(srv.get(NMS_SERVER)));
            srv.put(BUKKIT_VERSION_COMMAND, patchVersionCmd(srv.get(BUKKIT_VERSION_COMMAND)));
            // Add GC on reload / init to better manage RAM
            String CRAFT_SERVER = "org/bukkit/craftbukkit/" + NMS + "/CraftServer.class";
            srv.put(CRAFT_SERVER, patchGC(srv.get(CRAFT_SERVER), "reload", stats));
            String NMS_DEDICATED_SERVER = "net/minecraft/server/" + NMS + "/DedicatedServer.class";
            srv.put(NMS_DEDICATED_SERVER, patchGC(srv.get(NMS_DEDICATED_SERVER), "init", stats));
            byte[] paperJvmCheck = srv.get(PAPER_JVM_CHECKER);
            if (paperJvmCheck != null) {
                srv.put(PAPER_JVM_CHECKER, patchPaperJavaWarning(paperJvmCheck));
            }
            paperJvmCheck = srv.get(PAPER_JVM_CHECKER_OLD);
            if (paperJvmCheck != null) {
                srv.put(PAPER_JVM_CHECKER_OLD, patchPaperJavaWarning(paperJvmCheck));
            }
            if (compatibilityPatches) {
                // Add commonly used APIs on old plugins
                OnlinePlayersCompact.check(srv, MathHelper, stats);
                InventoryCompact.check(srv, MathHelper, stats);
                EntityCompact.check(srv, MathHelper, stats);
                PlayerPatcherCompact.check(srv, MathHelper, stats);
                GuavaVarTypeCompact.check(srv, MathHelper, stats);
                BukkitVarTypeCompact.check(srv, MathHelper, stats);
            }
            // Security patches
            BookCrashFixer.patch(srv, MathHelper, stats);
            AuthenticationHardening.patch(srv, MathHelper, stats);
            // Specific optimisations
            if (!isYatopiaPatched) {
                ChunkCacheOptimizer.patch(srv, MathHelper, stats);
                MethodResultCacheOptimizer.patch(srv, MathHelper, stats);
                BlockDataOptimiser.patch(srv, MathHelper, stats);
                PluginRewriteOptimiser.patch(srv, MathHelper, plRewrite);
                NMSAccessOptimizer.patch(srv);
            }
            // Add features patches
            if (featuresPatches) {
                DataCommandFeature.install(srv, MathHelper, stats);
                EntityPropertiesFeature.install(srv, inject, MathHelper, stats);
                BiomeConfigAPIFeature.install(srv, inject, MathHelper, classDataProvider, stats);
            }
            // Save in the jar if plugin rewrite is supported/installed
            manifest.getMainAttributes().putValue("Kibble-Rewrite",
                    isYatopiaPatched?"BUILT-IN":plRewrite[0]?"INSTALLED":"UNSUPPORTED");
        } else {
            // Add features in lib mode
            if (featuresPatches) {
                EntityPropertiesFeature.installLib(inject);
                BiomeConfigAPIFeature.installLib(srv, inject);
            }
        }
        if (compatibilityPatches) {
            // These classes are used by some plugins but are no longer available since java 9
            if (!srv.containsKey("javax/xml/bind/DatatypeConverter.class")) {
                inject.put("javax/xml/bind/DatatypeConverter.class",
                        readResource("javax/xml/bind/DatatypeConverter.class"));
                inject.put("javax/xml/bind/annotation/adapters/XmlAdapter.class",
                        readResource("javax/xml/bind/annotation/adapters/XmlAdapter.class"));
                inject.put("javax/xml/bind/annotation/adapters/HexBinaryAdapter.class",
                        readResource("javax/xml/bind/annotation/adapters/HexBinaryAdapter.class"));
            }
        }
        if (!this.yatopiaMode) {
            byte[] FastMathAPI = readResource("net/kibblelands/server/FastMath.class");
            byte[] FastReplaceAPI = readResource("net/kibblelands/server/FastReplace.class");
            if (!libraryMode) { // Mirror FastMath to MathHelper
                ClassNode classNode = new ClassNode();
                new ClassReader(FastMathAPI).accept(classNode, 0);
                for (MethodNode methodNode : classNode.methods) {
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
                classNode.accept(classWriter);
                FastMathAPI = classWriter.toByteArray();
                if (StringUtil != null) {
                    classNode = new ClassNode();
                    new ClassReader(FastReplaceAPI).accept(classNode, 0);
                    for (MethodNode methodNode : classNode.methods) {
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
                    classNode.accept(classWriter);
                    FastReplaceAPI = classWriter.toByteArray();
                }
            }
            inject.put("net/kibblelands/server/FastMath.class", FastMathAPI);
            inject.put("net/kibblelands/server/FastReplace.class", FastReplaceAPI);
        }
        // Optimise all classes
        srv.values().removeIf(Objects::isNull); // Clean null elements
        if (!libraryMode) {
            if (!isYatopiaPatched) {
                for (Map.Entry<String, byte[]> entry : srv.entrySet()) {
                    if (entry.getKey().endsWith(".class")) {
                        patchClassOpt(entry, classDataProvider, entry.getKey().startsWith("org/apache/commons/math3/") ||
                                entry.getKey().startsWith("net/minecraft/server/") ? null : MathHelper, stats, isYatopiaPatched);
                    } else if (entry.getKey().equals("pack.mcmeta") || entry.getKey().endsWith(".json")) {
                        trimJSON(entry);
                    }
                }
            }
            if (!this.yatopiaMode) {
                // Patch default config for performance
                if (srv.get("configurations/bukkit.yml") != null) {
                    srv.put("configurations/bukkit.yml", new String(srv.get("configurations/bukkit.yml"), StandardCharsets.UTF_8)
                            .replace("query-plugins: true", "query-plugins: false")
                            .replace("monster-spawns: 1", "monster-spawns: 2").replace("water-spawns: 1", "water-spawns: 2")
                            .replace("ambient-spawns: 1", "ambient-spawns: 2").getBytes(StandardCharsets.UTF_8));
                }
            }
            // Get stats in final jar
            manifest.getMainAttributes().putValue(this.yatopiaMode ? "Kibble-Yatopia-Stats" : "Kibble-Stats", Arrays.toString(stats));
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
        if (isYatopiaPatched) {
            logger.info("Generic optimiser: Skipped");
        } else {
            logger.info("Generic optimiser: ");
            logger.info("  Inlined calls: " + stats[0]);
            logger.info("  Optimised java calls: " + stats[1]);
            logger.info("  Optimised opcodes: " + stats[2]);
            if (externalPatches) {
                logger.info("  Optimised forEach: " + stats[6]);
            }
        }
        logger.info("Finished!\n");
        if (this.yatopiaMode) {
            return;
        }
        logger.info("Server patcher: ");
        logger.info("  Compatibility patches: "+stats[3]);
        logger.info("  Optimisations patches: "+stats[4]);
        logger.info("  Security patches: "+stats[5]);
        logger.info("  Plugin rewrite: "+(isYatopiaPatched?
                (ConsoleColors.CYAN + "BUILT-IN"):plRewrite[0]?
                (ConsoleColors.CYAN + "INSTALLED"):
                (ConsoleColors.YELLOW + "UNSUPPORTED")));
    }

    public static Map<String,byte[]> readZIP(final InputStream in) throws IOException {
        ZipInputStream inputStream = new ZipInputStream(in);
        Map<String,byte[]> items = new HashMap<>();
        ZipEntry entry;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[2048];
        int nRead;
        while (null!=(entry=inputStream.getNextEntry())) {
            if (!entry.isDirectory()) {
                baos.reset();
                while ((nRead = inputStream.read(buffer, 0, buffer.length)) != -1) {
                    baos.write(buffer, 0, nRead);
                }
                items.put(entry.getName(), baos.toByteArray());
            }
        }
        in.close();
        return items;
    }

    public static byte[] readResource(String path) throws IOException {
        InputStream inputStream = KibblePatcher.class.getClassLoader().getResourceAsStream(path);
        if (inputStream == null) {
            throw new FileNotFoundException(path);
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[2048];
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            baos.write(data, 0, nRead);
        }
        return baos.toByteArray();
    }

    public static void writeZIP(Map<String, byte[]> items, final OutputStream out) throws IOException {
        final ZipOutputStream zip = new ZipOutputStream(out);
        for (final String path : items.keySet()) {
            final byte[] data = items.get(path);
            final ZipEntry entry = new ZipEntry(path);
            zip.putNextEntry(entry);
            zip.write(data);
        }
        zip.flush();
        zip.close();
    }

    public static void patchZIP(final InputStream in, final OutputStream out, Manifest manifest, Map<String, byte[]> patch, Map<String, byte[]> inject) throws IOException {
        ZipInputStream inputStream = new ZipInputStream(in);
        KibbleOutputStream zip = new KibbleOutputStream(out, manifest);
        zip.setComment("Patched by Kibble "+KIBBLE_VERSION.replace('_', ' '));
        ZipEntry entry;
        byte[] buffer = new byte[2048];
        while (null!=(entry=inputStream.getNextEntry())) {
            if (entry.getName().equals(JarFile.MANIFEST_NAME) || (entry.getName().startsWith("javax/annotation/") && entry.getName().endsWith(".java"))) {
                continue;
            }
            entry.setMethod(ZipOutputStream.DEFLATED);
            if (entry.isDirectory()) {
                zip.putNextEntry(entry);
            } else {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                int nRead;
                while ((nRead = inputStream.read(buffer, 0, buffer.length)) != -1) {
                    baos.write(buffer, 0, nRead);
                }
                byte[] data = patch.getOrDefault(entry.getName(), baos.toByteArray());
                entry.setCompressedSize(-1);
                entry.setSize(data.length);
                zip.putNextEntry(entry);
                zip.write(data);
            }
            zip.closeEntry();
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

    public static byte[] patchDelayer(byte[] bytes) throws IOException {
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
                                value = 5L;
                            } else if (patch10 && (Long) value == 10L) {
                                patch10 = false;
                                value = 5L;
                            }
                        }
                        super.visitLdcInsn(value);
                    }
                };
            }
        }, 0);
        return classWriter.toByteArray();
    }

    public static byte[] patchPaperJavaWarning(byte[] bytes) throws IOException {
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

    public static byte[] patchBrand(byte[] bytes) throws IOException {
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
                            value = "\u00A7b" + srv + "\u00A7r";
                        }
                        super.visitLdcInsn(value);
                    }
                };
            }
        }, 0);
        return classWriter.toByteArray();
    }

    public static byte[] patchVersionCmd(byte[] bytes) throws IOException {
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
                                value = " \u00A7bKibble " + KIBBLE_VERSION + "\u00A7r" + (ldc.charAt(0) == ' ' ? "" : " ") + ldc;
                            }
                        }
                        super.visitLdcInsn(value);
                    }
                };
            }
        }, 0);
        return classWriter.toByteArray();
    }

    public static byte[] patchGC(byte[] bytes,String method,final int[] stats) throws IOException {
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

    public void patchClassOpt(Map.Entry<String, byte[]> p,ClassDataProvider cdp,
                              String Math,final int[] stats,final boolean isYatopia) throws IOException {
        patchClassOpt(p, cdp, Math, stats, isYatopia, false);
    }

    private static final String replaceDesc =
            "(Ljava/lang/String;Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;";

    private void patchClassOpt(final Map.Entry<String, byte[]> p,ClassDataProvider cdp,
                               String Math, final int[] stats,final boolean isYatopia,final boolean err) throws IOException {
        boolean[] requireCalc_dontOptimise = new boolean[]{false, false};
        ClassReader classReader = new ClassReader(p.getValue());
        ClassNode classNode = new ClassNode();
        classReader.accept(classNode, 0);
        if (externalPatches) {
            ForEachRemover.transform(classNode, cdp, stats);
        }
        ClassWriter classWriter = err ? new ClassWriter(0) : cdp.newClassWriter();
        if (isYatopia) { // Don't optimise if Yatopia based server
            classNode.accept(classWriter);
            requireCalc_dontOptimise[1] = (classNode.version < V1_6 && !classNode.name.startsWith("net/minecraft/server/")
                    && !(classNode.name.startsWith("org/bukkit/") && !classNode.name.startsWith("org/bukkit/craftbukkit/libs/")));
        } else {
            classNode.accept(new ClassVisitor(ASM_BUILD, classWriter) {
                @Override
                public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                    super.visit(version, access, name, signature, superName, interfaces);
                    requireCalc_dontOptimise[1] = (version < V1_6 && !name.startsWith("net/minecraft/server/")
                            && !(name.startsWith("org/bukkit/") && !name.startsWith("org/bukkit/craftbukkit/libs/")));
                }

                @Override
                public MethodVisitor visitMethod(int access, final String m_name, final String m_descriptor, String signature, String[] exceptions) {
                    if (requireCalc_dontOptimise[1]) return new MethodVisitor(ASM_BUILD) {
                    };
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
                                } else if (owner.equals("java/lang/Math") && (name.equals("sqrt") ||
                                        name.equals("sin") || name.equals("cos") || name.equals("tan")
                                        || name.equals("asin") || name.equals("acos") || name.equals("atan"))) {
                                    owner = "java/lang/StrictMath";
                                    stats[1]++;
                                }
                                if (!inline0(this, opcode, owner, name, descriptor, isInterface)) {
                                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                                } else {
                                    stats[0]++;
                                    requireCalc_dontOptimise[0] = true;
                                }
                                return;
                            } else if (opcode == INVOKEVIRTUAL && owner.equals("java/lang/String") &&
                                    name.equals("replace") && descriptor.contains("CharSequence")) {
                                opcode = INVOKESTATIC;
                                owner = KibblePatcher.this.yatopiaMode ?
                                        "org/yatopiamc/yatopia/server/util/FastReplace" :
                                        "net/kibblelands/server/FastReplace";
                                descriptor = replaceDesc;
                                stats[1]++;
                            }
                            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                        }

                        @Override
                        public void visitEnd() {
                            Optimizer.optimize(((MethodNode) this.mv), stats);
                            ((MethodNode) this.mv).accept(parentMethodVisitor);
                        }
                    };
                }
            });
        }
        if (err) {
            Files.write(new File("malformed.class").toPath(), classWriter.toByteArray());
        } else {
            if (requireCalc_dontOptimise[1]) {
                return;
            }
            byte[] data = classWriter.toByteArray();
            classWriter = requireCalc_dontOptimise[0] ? cdp.newClassWriter() : new ClassWriter(ClassWriter.COMPUTE_MAXS);
            new ClassReader(data).accept(new ClassVisitor(ASM_BUILD, classWriter) {
                @Override
                public MethodVisitor visitMethod(int access, String m_name, String m_descriptor, String signature, String[] exceptions) {
                    return new MethodVisitor(ASM_BUILD, super.visitMethod(access, m_name, m_descriptor, signature, exceptions)) {
                        @Override
                        public void visitMaxs(int maxStack, int maxLocals) {
                            try {
                                super.visitMaxs(maxStack, maxLocals);
                            } catch (Throwable t) {
                                try {
                                    patchClassOpt(p, cdp, Math, stats, isYatopia, true);
                                } catch (IOException ignored) {}
                                throw new RuntimeException("Malformed method at "+p.getKey()+"#"+m_name+m_descriptor, t);
                            }
                        }
                    };
                }
            }, 0);
            p.setValue(classWriter.toByteArray());
        }
    }

    public static void trimJSON(Map.Entry<String, byte[]> p) throws IOException {
        byte[] bytes = p.getValue();
        String str = new String(bytes, StandardCharsets.UTF_8);
        StringBuilder stringBuilder = new StringBuilder();
        int index = 0;
        boolean inString = false, special = false;
        while (index < str.length()) {
            char next = str.charAt(index);
            if (inString) {
                if (special) {
                    special = false;
                } else if (next == '\\') {
                    special = true;
                } else if (next == '\"') {
                    inString = false;
                }
            } else {
                if (next == '\"') {
                    inString = true;
                }
                switch (next) {
                    default:
                        break;
                    case '\"':
                        inString = true;
                        break;
                    case ' ':
                    case '\n':
                    case '\r':
                    case '\t':
                        index++;
                        continue;
                }
            }
            stringBuilder.append(next);
            index++;
        }
        p.setValue(stringBuilder.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static boolean inline0(MethodVisitor methodVisitor, int opcode, String owner, String name, String descriptor, boolean isInterface) {
        if (!owner.equals("java/lang/Math") && !owner.equals("java/lang/StrictMath")) {
            return false;
        }
        if (descriptor.indexOf('I') != -1) {
            switch (name) {
                default:
                    return false;
                case "abs": {
                    Label label = new Label();
                    methodVisitor.visitInsn(DUP);
                    methodVisitor.visitJumpInsn(IFGE, label);
                    methodVisitor.visitInsn(INEG);
                    methodVisitor.visitLabel(label);
                    break;
                }
                case "max": {
                    Label label = new Label();
                    methodVisitor.visitInsn(DUP2);
                    methodVisitor.visitJumpInsn(IF_ICMPGE, label);
                    methodVisitor.visitInsn(SWAP);
                    methodVisitor.visitLabel(label);
                    methodVisitor.visitInsn(POP);
                    break;
                }
                case "min": {
                    Label label = new Label();
                    methodVisitor.visitInsn(DUP2);
                    methodVisitor.visitJumpInsn(IF_ICMPLE, label);
                    methodVisitor.visitInsn(SWAP);
                    methodVisitor.visitLabel(label);
                    methodVisitor.visitInsn(POP);
                    break;
                }
            }
        } else if (descriptor.indexOf('D') != -1) {
            switch (name) {
                default:
                    return false;
                case "toRadians":
                    methodVisitor.visitLdcInsn(180D);
                    methodVisitor.visitInsn(DDIV);
                    methodVisitor.visitLdcInsn(Math.PI);
                    methodVisitor.visitInsn(DMUL);
                    break;
                case "toDegrees":
                    methodVisitor.visitLdcInsn(180D);
                    methodVisitor.visitInsn(DMUL);
                    methodVisitor.visitLdcInsn(Math.PI);
                    methodVisitor.visitInsn(DDIV);
                    break;
                case "abs": {
                    Label label = new Label();
                    methodVisitor.visitInsn(DUP2);
                    methodVisitor.visitInsn(DCONST_0);
                    methodVisitor.visitInsn(DCMPG);
                    methodVisitor.visitJumpInsn(IFGE, label);
                    methodVisitor.visitInsn(DNEG);
                    methodVisitor.visitLabel(label);
                    break;
                }
            }
        } else if (descriptor.indexOf('F') != -1) {
            switch (name) {
                default:
                    return false;
                case "abs": {
                    Label label = new Label();
                    methodVisitor.visitInsn(DUP);
                    methodVisitor.visitInsn(FCONST_0);
                    methodVisitor.visitInsn(FCMPG);
                    methodVisitor.visitJumpInsn(IFGE, label);
                    methodVisitor.visitInsn(FNEG);
                    methodVisitor.visitLabel(label);
                    break;
                }
                case "max": {
                    Label label = new Label();
                    methodVisitor.visitInsn(DUP2);
                    methodVisitor.visitInsn(FCMPL);
                    methodVisitor.visitJumpInsn(IFGE, label);
                    methodVisitor.visitInsn(SWAP);
                    methodVisitor.visitLabel(label);
                    methodVisitor.visitInsn(POP);
                    break;
                }
                case "min": {
                    Label label = new Label();
                    methodVisitor.visitInsn(DUP2);
                    methodVisitor.visitInsn(FCMPL);
                    methodVisitor.visitJumpInsn(IFLE, label);
                    methodVisitor.visitInsn(SWAP);
                    methodVisitor.visitLabel(label);
                    methodVisitor.visitInsn(POP);
                    break;
                }
            }
        } else {
            return false;
        }
        return true;
    }

    public Logger getLogger() {
        return logger;
    }
}
