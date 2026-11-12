package android.print;

import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import java.io.File;

/**
 * Writes a print adapter's output straight to a PDF file, without the print screen, so the report can be shared.
 * It sits in this package because Android only lets code here create the two callbacks. If a phone refuses that,
 * the caller catches the error and opens the print screen instead.
 */
public final class PdfWriter {
    public interface Done { void done(String error); }

    public static void write(final PrintDocumentAdapter ad, PrintAttributes attrs, final File out, final Done done) {
        ad.onLayout(null, attrs, null, new PrintDocumentAdapter.LayoutResultCallback() {
            @Override public void onLayoutFinished(PrintDocumentInfo info, boolean changed) {
                try {
                    final ParcelFileDescriptor fd = ParcelFileDescriptor.open(out, ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE | ParcelFileDescriptor.MODE_READ_WRITE);
                    ad.onWrite(new PageRange[]{PageRange.ALL_PAGES}, fd, new CancellationSignal(), new PrintDocumentAdapter.WriteResultCallback() {
                        @Override public void onWriteFinished(PageRange[] pages) { try { fd.close(); } catch (Exception ignored) {} ad.onFinish(); done.done(null); }
                        @Override public void onWriteFailed(CharSequence error) { try { fd.close(); } catch (Exception ignored) {} ad.onFinish(); done.done(String.valueOf(error)); }
                    });
                } catch (Exception e) { done.done(String.valueOf(e.getMessage())); }
            }
            @Override public void onLayoutFailed(CharSequence error) { done.done(String.valueOf(error)); }
        }, null);
    }
}
