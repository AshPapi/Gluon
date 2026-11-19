package net.ashpapi.papioptimization.client;

import java.util.Arrays;
import java.util.Objects;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSink;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class PapiFontSequenceKey {

    public static final int MAX_LEN = 96;

    private final int[] codepoints;
    private final Style[] styles;
    private final int contentHash;

    private PapiFontSequenceKey(int[] codepoints, Style[] styles, int contentHash) {
        this.codepoints = codepoints;
        this.styles = styles;
        this.contentHash = contentHash;
    }

    public boolean matches(Probe probe) {
        if (this.contentHash != probe.hash || this.codepoints.length != probe.len) {
            return false;
        }
        int[] cps = this.codepoints;
        int[] pCps = probe.codepoints;
        Style[] sts = this.styles;
        Style[] pSts = probe.styles;
        for (int i = 0; i < cps.length; i++) {
            if (cps[i] != pCps[i] || !Objects.equals(sts[i], pSts[i])) {
                return false;
            }
        }
        return true;
    }

    public static PapiFontSequenceKey copyFrom(Probe probe) {
        int len = probe.len;
        return new PapiFontSequenceKey(
                Arrays.copyOf(probe.codepoints, len),
                Arrays.copyOf(probe.styles, len),
                probe.hash
        );
    }

    public static final class Probe implements FormattedCharSink {

        final int[] codepoints = new int[MAX_LEN];
        final Style[] styles = new Style[MAX_LEN];
        int len;
        int hash;
        boolean rejected;

        public void reset() {
            this.len = 0;
            this.hash = 1;
            this.rejected = false;
        }

        public void clearReferences() {
            if (this.len > 0) {
                Arrays.fill(this.styles, 0, this.len, null);
                this.len = 0;
            }
        }

        public int length() {
            return this.len;
        }

        public int hash() {
            return this.hash;
        }

        public boolean isRejected() {
            return this.rejected;
        }

        @Override
        public boolean accept(int index, Style style, int codePoint) {
            if (this.len >= MAX_LEN || style == null || style.isObfuscated()) {
                this.rejected = true;
                return false;
            }
            int i = this.len++;
            this.codepoints[i] = codePoint;
            this.styles[i] = style;
            int h = this.hash;
            h = 31 * h + codePoint;
            h = 31 * h + style.hashCode();
            this.hash = h;
            return true;
        }
    }
}
