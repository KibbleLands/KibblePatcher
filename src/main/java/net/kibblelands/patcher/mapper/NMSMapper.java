package net.kibblelands.patcher.mapper;

public class NMSMapper {
    protected final String nms;

    public NMSMapper(String nms) {
        this.nms = nms;
    }

    public String mapClass(String text) {
        return text.replace("$NMS", this.nms);
    }

    public String mapDesc(String text) {
        return text.replace("$NMS", this.nms);
    }

    public String mapMethodName(String owner,String name) {
        return name;
    }

    public String mapFieldName(String owner,String name) {
        return name;
    }

    @Override
    public String toString() {
        return "NMS:" + this.nms;
    }
}
