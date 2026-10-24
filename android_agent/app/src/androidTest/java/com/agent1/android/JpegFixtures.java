package com.agent1.android;

import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import java.io.File;
import java.io.FileOutputStream;

/** Solid-gray JPEGs for weizhi:video v2 image-track smokes (no committed binaries). */
public final class JpegFixtures {
    private JpegFixtures() {}

    public static File gray(File dir, String name, int width, int height, int gray)
            throws Exception {
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bmp.eraseColor(0xFF000000 | (gray << 16) | (gray << 8) | gray);
        File out = new File(dir, name);
        try (FileOutputStream fos = new FileOutputStream(out)) {
            assertTrue("jpeg compress failed", bmp.compress(Bitmap.CompressFormat.JPEG, 95, fos));
        } finally {
            bmp.recycle();
        }
        return out;
    }
}
