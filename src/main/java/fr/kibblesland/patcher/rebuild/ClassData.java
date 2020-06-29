package fr.kibblesland.patcher.rebuild;

public interface ClassData {
    String getName();
    ClassData getSuperclass();
    ClassData[] getInterfaces();
    boolean isAssignableFrom(ClassData clData);
    boolean isInterface();
    boolean isFinal();
    boolean isPublic();
    boolean isCustom();
}
