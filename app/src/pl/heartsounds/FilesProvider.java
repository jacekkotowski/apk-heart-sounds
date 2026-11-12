package pl.heartsounds;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/** Hands one recording, or one report PDF, to the app the user picked in the share sheet. */
public class FilesProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    private File file(Uri u) throws FileNotFoundException {
        String name = new File(u.getLastPathSegment()).getName(); boolean report = u.getPathSegments().size() == 2 && "report".equals(u.getPathSegments().get(0));
        File f = new File(report ? MainActivity.reportDir(getContext()) : MainActivity.dir(getContext()), name);
        if (!f.exists()) throw new FileNotFoundException();
        return f;
    }
    @Override public ParcelFileDescriptor openFile(Uri u, String m) throws FileNotFoundException { return ParcelFileDescriptor.open(file(u), ParcelFileDescriptor.MODE_READ_ONLY); }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) {
        MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        try { File f = file(u); c.addRow(new Object[]{f.getName(), f.length()}); } catch (FileNotFoundException ignored) {}
        return c;
    }
    @Override public String getType(Uri u) { return String.valueOf(u.getLastPathSegment()).endsWith(".pdf") ? "application/pdf" : "audio/wav"; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
