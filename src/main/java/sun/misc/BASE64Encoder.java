package sun.misc;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Deprecated
public class BASE64Encoder {
    @Deprecated
    public BASE64Encoder() {}

    @Deprecated
    public String encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Deprecated
    public void encode(byte[] bytes, OutputStream outputStream) throws IOException {
        outputStream.write(encode(bytes).getBytes(StandardCharsets.US_ASCII));
    }

    @Deprecated
    public String encode(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return encode(bytes);
    }

    @Deprecated
    public void encode(ByteBuffer buffer, OutputStream outputStream) throws IOException {
        outputStream.write(encode(buffer).getBytes(StandardCharsets.US_ASCII));
    }

    @Deprecated
    public String encodeBuffer(byte[] bytes) {
        return this.encode(bytes);
    }

    @Deprecated
    public void encodeBuffer(byte[] bytes, OutputStream outputStream) throws IOException {
        this.encode(bytes, outputStream);
    }

    @Deprecated
    public String encodeBuffer(ByteBuffer buffer) {
        return this.encode(buffer);
    }

    @Deprecated
    public void encodeBuffer(ByteBuffer buffer, OutputStream outputStream) throws IOException {
        this.encode(buffer, outputStream);
    }
}
