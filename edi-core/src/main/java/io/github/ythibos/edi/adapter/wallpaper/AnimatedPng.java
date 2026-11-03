package io.github.ythibos.edi.adapter.wallpaper;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.zip.CRC32;

final class AnimatedPng {

    private static final int PLAYS_FOREVER = 0;
    private static final int CENTISECONDS = 100;
    private static final int LONGEST_DELAY = 0xFFFF;
    private static final byte KEEP_THE_FRAME = 0;
    private static final byte REPLACE_THE_REGION = 0;

    private final ByteArrayOutputStream animation = new ByteArrayOutputStream();
    private int sequence;

    private AnimatedPng() {
    }

    static byte[] looping(List<Still> stills) {
        PngImage canvas = canvasOf(stills);
        AnimatedPng png = new AnimatedPng();
        png.animation.writeBytes(PngImage.signature());
        png.chunk("IHDR", header(canvas));
        png.chunk("acTL", ByteBuffer.allocate(8).putInt(stills.size()).putInt(PLAYS_FOREVER).array());
        for (Still still : stills) {
            png.frame(canvas, still, still == stills.getFirst());
        }
        png.chunk("IEND", new byte[0]);
        return png.animation.toByteArray();
    }

    private static PngImage canvasOf(List<Still> stills) {
        if (stills.isEmpty()) {
            throw new IllegalArgumentException("An animation has at least one frame.");
        }
        PngImage canvas = stills.getFirst().image();
        if (canvas.left() != 0 || canvas.top() != 0) {
            throw new IllegalArgumentException("An animation's first frame covers the whole canvas.");
        }
        for (Still still : stills) {
            if (!still.image().sharesTheFormatOf(canvas) || !still.image().fitsWithin(canvas)) {
                throw new IllegalArgumentException("Every frame of an animation shares the first frame's format and fits within it.");
            }
        }
        return canvas;
    }

    private static byte[] header(PngImage canvas) {
        return ByteBuffer.allocate(13).putInt(canvas.width()).putInt(canvas.height()).put(canvas.format()).array();
    }

    private void frame(PngImage canvas, Still still, boolean first) {
        PngImage image = still.image();
        chunk("fcTL", ByteBuffer.allocate(26).putInt(sequence++).putInt(image.width()).putInt(image.height()).putInt(image.left())
                .putInt(image.top()).putShort(delayOf(still.shown())).putShort((short) CENTISECONDS).put(KEEP_THE_FRAME)
                .put(REPLACE_THE_REGION).array());
        for (byte[] data : image.data()) {
            if (first) {
                chunk("IDAT", data);
            } else {
                chunk("fdAT", ByteBuffer.allocate(4 + data.length).putInt(sequence++).put(data).array());
            }
        }
    }

    private static short delayOf(Duration shown) {
        long centiseconds = shown.toMillis() / (1000 / CENTISECONDS);
        if (centiseconds < 1 || centiseconds > LONGEST_DELAY) {
            throw new IllegalArgumentException("A frame is shown for between 10 milliseconds and " + LONGEST_DELAY / CENTISECONDS + " seconds.");
        }
        return (short) centiseconds;
    }

    private void chunk(String type, byte[] body) {
        byte[] typed = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 check = new CRC32();
        check.update(typed);
        check.update(body);
        animation.writeBytes(ByteBuffer.allocate(4).putInt(body.length).array());
        animation.writeBytes(typed);
        animation.writeBytes(body);
        animation.writeBytes(ByteBuffer.allocate(4).putInt((int) check.getValue()).array());
    }

    record Still(PngImage image, Duration shown) {
    }
}
