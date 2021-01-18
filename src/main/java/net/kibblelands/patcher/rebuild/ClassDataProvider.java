package net.kibblelands.patcher.rebuild;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.security.SecureClassLoader;
import java.util.*;

import static net.kibblelands.patcher.utils.ASMUtils.ASM_BUILD;
import static org.objectweb.asm.Opcodes.*;

public class ClassDataProvider {
    private static final ClassLoader BOOTSTRAP_CLASS_LOADER = new SecureClassLoader(null) {};

    private static final ClData object;
    private static final ClData objectArray;

    static {
        object = new ObjectCLData();
        objectArray = new ObjectCLDataArray();
    }

    private final HashMap<String,ClData> clDataHashMap;
    private final ClassLoader classLoader;

    public ClassDataProvider() {
        this(null);
    }

    public ClassDataProvider(ClassLoader classLoader) {
        this.clDataHashMap = new HashMap<>();
        this.clDataHashMap.put("java/lang/Object", object);
        this.clDataHashMap.put("[java/lang/Object", objectArray);
        this.classLoader = classLoader==null? ClassLoader.getSystemClassLoader():classLoader;
    }

    public static class ClData implements ClassData {

        final String name;
        String superClass;
        int access;

        private ClData(String name) {
            this.name = name;
            this.access = ACC_PUBLIC;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isInterface() {
            return Modifier.isInterface(this.access);
        }

        @Override
        public boolean isFinal() {
            return Modifier.isFinal(this.access);
        }

        @Override
        public boolean isPublic() {
            return Modifier.isPublic(this.access);
        }

        @Override
        public boolean isCustom() {
            return false;
        }

        @Override
        public ClData getSuperclass() {
            return null;
        }

        @Override
        public ClassData[] getInterfaces() {
            return new ClassData[0];
        }

        @Override
        public boolean isAssignableFrom(ClassData clData) {
            while (clData != null) {
                if (clData.getName().equals(this.name)) return true;
                clData = clData.getSuperclass();
            }
            return false;
        }

    }

    private static final class ObjectCLData extends ClData {
        private ObjectCLData() {
            super("java/lang/Object");
        }

        @Override
        public boolean isAssignableFrom(ClassData clData) {
            return clData == this;
        }
    }

    private static final class ObjectCLDataArray extends ClData {
        private ObjectCLDataArray() {
            super("[java/lang/Object");
        }

        @Override
        public boolean isAssignableFrom(ClassData clData) {
            return clData == this || clData == object;
        }

        @Override
        public ClData getSuperclass() {
            return object;
        }
    }

    class ClData2 extends ClData {
        List<String> interfaces;
        List<String> guessedSup;
        boolean custom = false;

        private ClData2(String name) {
            super(name);
        }

        @Override
        public ClData getSuperclass() {
            if (this.superClass==null) return null;
            return getClassData(this.superClass);
        }

        @Override
        public ClassData[] getInterfaces() {
            if (interfaces == null) {
                return new ClassData[0];
            }
            ClassData[] classData = new ClassData[interfaces.size()];
            int i = 0;
            for (String inName:interfaces) {
                classData[i] = getClassData(inName, true);
                i++;
            }
            return classData;
        }

        @Override
        public boolean isAssignableFrom(ClassData clData) {
            if (clData == null) return false;
            if (clData instanceof ClData2) {
                if (((ClData2) clData).interfaces != null) {
                    for (String cl : ((ClData2) clData).interfaces) {
                        if (this.isAssignableFrom(getClassData(cl))) {
                            return true;
                        }
                    }
                }
                if (((ClData2) clData).guessedSup != null) {
                    for (String cl : ((ClData2) clData).guessedSup) {
                        if (this.isAssignableFrom(getClassData(cl))) {
                            return true;
                        }
                    }
                }
            }
            do {
                if (clData == this) return true;
                clData = clData.getSuperclass();
            } while (clData != null);
            return false;
        }

        @Override
        public boolean isCustom() {
            return custom;
        }
    }

    class ClData2Array extends ClData {
        private final ClData clData;

        private ClData2Array(ClData clData) {
            super("["+clData.getName());
            this.clData = clData;
            this.access = clData.access;
        }

        @Override
        public ClData getSuperclass() {
            return ClassDataProvider.this.getClassData("["+clData.superClass);
        }

        @Override
        public boolean isCustom() {
            return clData.isCustom();
        }
    }

    public ClData getClassData(String clName) {
        return this.getClassData(clName, false);
    }

    private ClData getClassData(String clName,boolean defaultToInterface) {
        if (clName.endsWith(";")) {
            throw new IllegalArgumentException("Can't put desc as class Data -> "+clName);
        }
        clName = clName.replace('.','/');
        ClData clData = clDataHashMap.get(clName);
        if (clData!=null) return clData;
        if (clName.startsWith("[")) {
            clDataHashMap.put(clName, clData = new ClData2Array(getClassData(clName.substring(1))));
            return clData;
        }
        clData = new ClData2(clName);
        try {
            ClassReader classReader = new ClassReader(Objects.requireNonNull(this.classLoader.getResourceAsStream(clName + ".class")));
            final ClData2 tClData = (ClData2) clData;
            classReader.accept(new ClassVisitor(ASM_BUILD) {
                @Override
                public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                    tClData.access = access;
                    tClData.superClass = superName;
                    if (interfaces != null && interfaces.length != 0) {
                        tClData.interfaces = Arrays.asList(interfaces);
                    }
                }
            }, ClassReader.SKIP_CODE);
        } catch (Exception e) {
            try { // Try to use the boot class loader (Help fix issues on Java9+)
                Class<?> cl = Class.forName(clName.replace('/','.'), false, BOOTSTRAP_CLASS_LOADER);
                clData.access = cl.getModifiers();
                clData.superClass = cl.getGenericSuperclass().getTypeName().replace('.','/');
                Type[] classes = cl.getGenericInterfaces();
                if (classes.length != 0) {
                    String[] interfaces = new String[classes.length];
                    for (int i = 0; i < interfaces.length;i++) {
                        interfaces[i] = classes[i].getTypeName().replace('.','/');
                    }
                    ((ClData2) clData).interfaces = Arrays.asList(interfaces);
                }
            } catch (Exception e2) {
                clData.superClass = "java/lang/Object";
                if (defaultToInterface) {
                    clData.access |= ACC_INTERFACE | ACC_ABSTRACT;
                }
            }
        }
        clDataHashMap.put(clName,clData);
        return clData;
    }

    public ClassWriter newClassWriter() {
        return this.newClassWriter(null);
    }

    public ClassWriter newClassWriter(ClassReader classReader) {
        return new ClassWriter(classReader, ClassWriter.COMPUTE_FRAMES) {
            @Override
            protected String getCommonSuperClass(String type1, String type2) {
                if (type1.equals(type2)) return type1;
                if (type1.equals("java/lang/Object") || type2.equals("java/lang/Object")) return "java/lang/Object";
                try {
                    ClData c, d;
                    try {
                        c = getClassData(type1);
                        d = getClassData(type2);
                    } catch (Exception e) {
                        throw new RuntimeException(e.toString());
                    }
                    if (c.isAssignableFrom(d)) {
                        return type1;
                    }
                    if (d.isAssignableFrom(c)) {
                        return type2;
                    }
                    if (c.isInterface() || d.isInterface()) {
                        return "java/lang/Object";
                    } else {
                        do {
                            c = c.getSuperclass();
                        } while (!c.isAssignableFrom(d));
                        return c.getName().replace('.', '/');
                    }
                } catch (Exception e) {
                    return "java/lang/Object";
                }
            }
        };
    }

    public void addClasses(Map<String, byte[]> classes) {
        for (Map.Entry<String, byte[]> entry:classes.entrySet()) if (entry.getKey().endsWith(".class")) {
            String name = entry.getKey().substring(0,entry.getKey().length()-6);
            ClData2 clData = new ClData2(name);
            try {
                ClassReader classReader = new ClassReader(entry.getValue());
                classReader.accept(new ClassVisitor(ASM_BUILD) {
                    @Override
                    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                        clData.access = access;
                        clData.superClass = superName;
                        clData.custom = true;
                        if (interfaces != null && interfaces.length != 0) {
                            clData.interfaces = Arrays.asList(interfaces);
                        }
                    }
                }, ClassReader.SKIP_CODE);
            } catch (Exception e) {
                clData.superClass = "java/lang/Object";
            }
            clDataHashMap.put(name,clData);
        }
    }
}
