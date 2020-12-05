package sun.misc;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Base64;

@Deprecated
public class BASE64Decoder {
    @Deprecated
    public BASE64Decoder() {}

    @Deprecated
    public byte[] decodeBuffer(String str) throws IOException {
        return Base64.getDecoder().decode(str);
    }

    @Deprecated
    public ByteBuffer decodeBufferToByteBuffer(String str) throws IOException {
        return ByteBuffer.wrap(decodeBuffer(str));
    }
}
