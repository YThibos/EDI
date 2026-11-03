package io.github.ythibos.edi.adapter.wallpaper;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

record PngImage(int width, int height, int left, int top, byte[] format, List<byte[]> data) {

    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final int CHUNK_FRAME = 12;

    PngImage {
        format = format.clone();
        data = List.copyOf(data);
    }

    static PngImage read(byte[] png) {
        refuseWithoutTheSignature(png);
        Reading reading = new Reading();
        ByteBuffer content = ByteBuffer.wrap(png, SIGNATURE.length, png.length - SIGNATURE.length);
        while (content.remaining() >= CHUNK_FRAME) {
            int length = content.getInt();
            String type = new String(new byte[] {content.get(), content.get(), content.get(), content.get()}, StandardCharsets.US_ASCII);
            byte[] body = new byte[length];
            content.get(body).getInt();
            reading.take(type, body);
        }
        return reading.image();
    }

    static byte[] signature() {
        return SIGNATURE.clone();
    }

    boolean sharesTheFormatOf(PngImage other) {
        return Arrays.equals(format, other.format);
    }

    boolean fitsWithin(PngImage canvas) {
        return left >= 0 && top >= 0 && left + width <= canvas.width && top + height <= canvas.height;
    }

    private static void refuseWithoutTheSignature(byte[] png) {
        if (png.length < SIGNATURE.length || !Arrays.equals(png, 0, SIGNATURE.length, SIGNATURE, 0, SIGNATURE.length)) {
            throw new IllegalArgumentException("A wallpaper frame is a PNG image.");
        }
    }

    private static final class Reading {

        private ByteBuffer header;
        private int left;
        private int top;
        private final List<byte[]> data = new ArrayList<>();

        void take(String type, byte[] body) {
            switch (type) {
                case "IHDR" -> header = ByteBuffer.wrap(body);
                case "oFFs" -> placeAt(ByteBuffer.wrap(body));
                case "IDAT" -> data.add(body);
                default -> {
                }
            }
        }

        private void placeAt(ByteBuffer offset) {
            left = offset.getInt();
            top = offset.getInt();
        }

        PngImage image() {
            if (header == null || data.isEmpty()) {
                throw new IllegalArgumentException("A wallpaper frame has a header and image data.");
            }
            byte[] format = Arrays.copyOfRange(header.array(), 8, header.capacity());
            return new PngImage(header.getInt(0), header.getInt(4), left, top, format, data);
        }
    }
}
