package com.foobnix.drive;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.foobnix.android.utils.Apps;
import com.foobnix.android.utils.IO;
import com.foobnix.android.utils.LOG;
import com.foobnix.android.utils.TxtUtils;
import com.foobnix.model.AppState;
import com.foobnix.pdf.info.AppsConfig;
import com.foobnix.pdf.info.ExtUtils;
import com.foobnix.ui2.BooksService;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.FileContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GFile {
    public static final int REQUEST_CODE_SIGN_IN = 1110;

    public static final String MIME_FOLDER = "application/vnd.google-apps.folder";

    public static final String TAG = "GFile";
    public static final int PAGE_SIZE = 999;

    public static com.google.api.services.drive.Drive googleDriveService;

    public static String debugOut = new String();


    public static String getDisplayInfo(Context c) {
        final GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(c);
        if (account == null) {
            return "";
        }
        return account.getDisplayName() + " (" + account.getEmail() + ")";

    }

    public static void logout(Context c) {
        GoogleSignInOptions signInOptions =
                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestEmail()
                        .requestScopes(new Scope(DriveScopes.DRIVE_FILE))
                        .build();
        GoogleSignInClient client = GoogleSignIn.getClient(c, signInOptions);
        client.signOut();
        googleDriveService = null;

    }

    public static void init(Activity c) {

        if (googleDriveService != null) {
            return;
        }

        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(c);

        if (account == null) {


            GoogleSignInOptions signInOptions =
                    new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestEmail()
                            .requestScopes(new Scope(DriveScopes.DRIVE_FILE))
                            .build();
            GoogleSignInClient client = GoogleSignIn.getClient(c, signInOptions);

            // The result of the sign-in Intent is handled in onActivityResult.
            c.startActivityForResult(client.getSignInIntent(), REQUEST_CODE_SIGN_IN);
        } else {

            GoogleAccountCredential credential =
                    GoogleAccountCredential.usingOAuth2(
                            c, Collections.singleton(DriveScopes.DRIVE_FILE));
            credential.setSelectedAccount(account.getAccount());
            googleDriveService =
                    new com.google.api.services.drive.Drive.Builder(
                            AndroidHttp.newCompatibleTransport(),
                            new GsonFactory(),
                            credential)
                            .setApplicationName(Apps.getApplicationName(c))
                            .build();
        }

    }

    public static void buildDriveService(Context c) {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(c);
        if (account == null) {
            LOG.d(TAG, "buildDriveService", " account is null");
            return;
        }

        if (googleDriveService != null) {
            LOG.d(TAG, "googleDriveService", " has already inited");
            return;
        }


        GoogleAccountCredential credential =
                GoogleAccountCredential.usingOAuth2(
                        c, Collections.singleton(DriveScopes.DRIVE_FILE));
        credential.setSelectedAccount(account.getAccount());
        googleDriveService =
                new com.google.api.services.drive.Drive.Builder(
                        AndroidHttp.newCompatibleTransport(),
                        new GsonFactory(),
                        credential)
                        .setApplicationName(Apps.getApplicationName(c))
                        .build();

        LOG.d(TAG, "googleDriveService", " build");

    }

    public static List<File> exeQF(String q, String... args) throws IOException {
        return exeQ(String.format(q, args));
    }

    public static List<File> exeQ(String q) throws IOException {
        //LOG.d(TAG, "exeQ", q);
        String nextPageToken;
        List<File> res = new ArrayList<File>();
        do {
            final FileList list = googleDriveService.files().list().setQ(q).setFields("nextPageToken, files(*)").setSpaces("drive").setPageSize(PAGE_SIZE).execute();
            nextPageToken = list.getNextPageToken();
            res.addAll(list.getFiles());
            LOG.d("nextPageToken",nextPageToken);
        } while (nextPageToken != null);
        return res;
    }

    public static List<File> getFiles(String rootId, long lastModifiedTime) throws Exception {

        String time = new DateTime(lastModifiedTime).toString();
        LOG.d("getFiles-by", rootId, time);
        final String txt = "('%s' in parents and trashed = false and modifiedTime>'%s') or ('%s' in parents and trashed = false and mimeType = '%s')";
        return exeQF(txt, rootId, time, rootId, MIME_FOLDER);
    }

    public static File findLibreraSync() throws Exception {

        final List<File> files = exeQF("name = 'Librera' and 'root' in parents and mimeType = '%s' and trashed = false", MIME_FOLDER);
        debugPrint(files);
        for (File it : files) {
            if (it.getParents() == null) {
                return it;
            }
        }
        return null;
    }

    public static void debugPrint(List<File> list) {

        LOG.d(TAG, list.size());
        for (File f : list) {
            LOG.d(TAG, f.getId(), f.getName(), f.getMimeType(), f.getParents(), f.getCreatedTime(), f.getModifiedTime(), "trashed", f.getTrashed());
            LOG.d(f);
        }
    }

    public static File getFileById(String roodId, String name) throws IOException {
        LOG.d(TAG, "Get file", roodId, name);
        name = name.replace("'", "\\'");
        final List<File> files = exeQF("'%s' in parents and name='%s' and trashed = false", roodId, name);
        if (files != null && files.size() >= 1) {
            final File file = files.get(0);
            return file;
        }

        return null;
    }

    public static File getOrCreateLock(String roodId, long modifiedTime) throws IOException {
        File file = getFileById(roodId, "lock");
        if (file == null) {
            File metadata = new File()
                    .setParents(Collections.singletonList(roodId))
                    .setModifiedTime(new DateTime(modifiedTime))
                    .setMimeType("text/plain")
                    .setName("lock");

            LOG.d(TAG, "Create lock", roodId, "lock");
            debugOut += "\nCreate lock: " + new DateTime(modifiedTime).toStringRfc3339();
            file = googleDriveService.files().create(metadata).execute();
        }
        return file;
    }

    public static void updateLock(String roodId, long modifiedTime) throws IOException {
        File file = getOrCreateLock(roodId, modifiedTime);
        File metadata = new File().setModifiedTime(new DateTime(modifiedTime));

        debugOut += "\nUpdate lock: " + new DateTime(modifiedTime).toStringRfc3339();
        GFile.googleDriveService.files().update(file.getId(), metadata).execute();
    }

    public static File createFile(String roodId, String name, String content, long lastModifiedtime) throws IOException {
        File file = getFileById(roodId, name);
        if (file == null) {
            File metadata = new File()
                    .setParents(Collections.singletonList(roodId))
                    .setModifiedTime(new DateTime(lastModifiedtime))
                    .setMimeType("text/plain")
                    .setName(name);

            LOG.d(TAG, "Create file", roodId, name);
            file = googleDriveService.files().create(metadata).execute();
        }

        File metadata = new File().setName(name).setModifiedTime(new DateTime(lastModifiedtime));
        ByteArrayContent contentStream = ByteArrayContent.fromString("text/plain", content);
        LOG.d(TAG, "Create file with content", roodId, name);
        GFile.googleDriveService.files().update(file.getId(), metadata, contentStream).execute();

        return file;
    }


    public static File getFileInfo(String roodId, final java.io.File inFile) throws IOException {
        File file = getFileById(roodId, inFile.getName());
        if (file == null) {
            File metadata = new File()
                    .setParents(Collections.singletonList(roodId))
                    .setMimeType(ExtUtils.getMimeType(inFile))
                    .setModifiedTime(new DateTime(inFile.lastModified()))
                    .setName(inFile.getName());

            LOG.d(TAG, "Create file", roodId, inFile.getName());
            file = googleDriveService.files().create(metadata).execute();
        }
        return file;

    }


    public static void uploadFile(String roodId, String fileId, final java.io.File inFile, long lastModified) throws IOException {
        if (lastModified == 0) {
            lastModified = inFile.lastModified();
        }
        File metadata = new File().setName(inFile.getName()).setModifiedTime(new DateTime(lastModified));
        FileContent contentStream = new FileContent("text/plain", inFile);
        LOG.d(TAG, "BIG upload file", roodId, inFile);
        googleDriveService.files().update(fileId, metadata, contentStream).execute();
        setLastModifiedTime(inFile, lastModified);
    }


    public static String readFileAsString(String fileId) throws IOException {

        LOG.d(TAG, "read file as string", fileId);
        //File metadata = googleDriveService.files().get(fileId).execute();
        //String name = metadata.getName();

        // Stream the file contents to a String.
        try (InputStream is = googleDriveService.files().get(fileId).executeMediaAsInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder stringBuilder = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
            String contents = stringBuilder.toString();

            return contents;
        }


    }


    public static void downloadFile(String fileId, java.io.File file, long lastModified) throws IOException {
        LOG.d(TAG, "BIG download file", fileId, file.getPath());
        InputStream is = null;
        //if (!file.getPath().endsWith("json")) {
        //    is = googleDriveService.files().get(fileId).executeMediaAsInputStream();
        //} else {
        // }
        try {
            is = googleDriveService.files().get(fileId).executeMediaAsInputStream();
        } catch (Exception e) {
            is = googleDriveService.files().get(fileId).executeAsInputStream();
        }

        IO.copyFile(is, file);
        LOG.d(TAG, "downloadFile-lastModified before", file.lastModified(), lastModified, file.getName());

        setLastModifiedTime(file, lastModified);

        LOG.d(TAG, "downloadFile-lastModified after", file.lastModified(), lastModified, file.getName());

    }

    @TargetApi(Build.VERSION_CODES.O)
    public static boolean setLastModifiedTime(java.io.File file, long lastModified) {
        if (lastModified > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                Files.setLastModifiedTime(Paths.get(file.getPath()), FileTime.fromMillis(lastModified));
            } catch (IOException e) {
                LOG.e(e);
                return false;
            }
        }
        return true;
    }

    public static void deleteBook(String rootId, String name) throws IOException {
        name = name.replace("'", "\\'");
        final List<File> files = exeQF("'%s' in parents and name = '%s'", rootId, name);
        for (File f : files) {
            File metadata = new File().setTrashed(true);
            LOG.d("Delete book", name);
            debugOut += "\nDelete book: " + name;
            googleDriveService.files().update(f.getId(), metadata).execute();
        }
    }


    public static File createFolder(String roodId, String name) throws IOException {
        File folder = getFileById(roodId, name);
        if (folder != null) {
            return folder;
        }
        LOG.d(TAG, "Create folder", roodId, name);
        debugOut += "\nCreate folder: " + name;
        File metadata = new File()
                .setParents(Collections.singletonList(roodId))
                .setMimeType(MIME_FOLDER)
                .setName(name);

        return googleDriveService.files().create(metadata).execute();

    }


    public static synchronized void sycnronizeAll(final Context c, long myLastSyncTime) throws Exception {

        try {
            buildDriveService(c);
            LOG.d(TAG, "sycnronizeAll", "begin");
            if (TxtUtils.isEmpty(AppState.get().syncRootID)) {
                LOG.d(TAG, "syncRootID", AppState.get().syncRootID);
                File syncRoot = GFile.findLibreraSync();
                LOG.d(TAG, "findLibreraSync", syncRoot);
                if (syncRoot == null) {
                    syncRoot = GFile.createFolder("root", "Librera");
                }
                AppState.get().syncRootID = syncRoot.getId();
                AppState.get().save(c);
            }


            debugOut += "\nBegin from: " + new DateTime(myLastSyncTime).toStringRfc3339();

            sync(AppState.get().syncRootID, AppsConfig.SYNC_FOLDER_ROOT, myLastSyncTime);

            //updateLock(AppState.get().syncRootID, beginTime);

            LOG.d(TAG, "sycnronizeAll", "finished");
            debugOut += "\nEnd: " + new DateTime(myLastSyncTime).toStringRfc3339();

        } catch (Exception e) {
            debugOut += "\nException: " + e.getMessage();
            LOG.e(e);
            throw e;
        }
    }


    private static void sync(String syncId, final java.io.File ioRoot, long lastModified) throws
            Exception {
        LOG.d(TAG, "sync", syncId, ioRoot.getPath());
        final java.io.File[] files = ioRoot.listFiles();

        if (files == null) {
            return;
        }

        for (java.io.File file : files) {
            if (file.isDirectory()) {
                File folder = createFolder(syncId, file.getName());
                sync(folder.getId(), file, lastModified);
            } else {
                if (file.lastModified() > lastModified) {
                    final File syncFile = getFileInfo(syncId, file);
                    //LOG.d(TAG, "sync-file", syncFile.getName(), syncFile.getModifiedTime().getValue(), file.lastModified());
                    if (syncFile.getModifiedTime() == null || syncFile.getModifiedTime().getValue() / PAGE_SIZE < file.lastModified() / PAGE_SIZE) {
                        debugOut += "\nUpload: " + file.getName();
                        uploadFile(syncId, syncFile.getId(), file, lastModified);
                    } else if (syncFile.getModifiedTime().getValue() / PAGE_SIZE > file.lastModified() / PAGE_SIZE) {
                        debugOut += "\nDownload: " + file.getName();
                        downloadFile(syncId, file, syncFile.getModifiedTime().getValue());
                    }
                }
            }
        }
        final List<File> rFiles = getFiles(syncId, lastModified);
        for (File remote : rFiles) {
            if (!MIME_FOLDER.equals(remote.getMimeType())) {
                java.io.File lFile = new java.io.File(ioRoot, remote.getName());
                if (!lFile.exists() || remote.getModifiedTime().getValue() / PAGE_SIZE > lFile.lastModified() / PAGE_SIZE) {
                    debugOut += "\nDownload: " + lFile.getName();
                    downloadFile(remote.getId(), lFile, remote.getModifiedTime().getValue());
                }
            } else {
                java.io.File lFile = new java.io.File(ioRoot, remote.getName());
                if (!lFile.exists()) {
                    debugOut += "\nCreate dir: " + lFile.getName();
                    lFile.mkdirs();
                    sync(remote.getId(), lFile, lastModified);
                }
            }
        }


    }

    public static void runSyncService(Activity a) {
        if (AppState.get().isEnableGdrive && !BooksService.isRunning) {
            GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(a);
            if (account != null) {
                GFile.buildDriveService(a);
                a.startService(new Intent(a, BooksService.class).setAction(BooksService.ACTION_RUN_SYNCRONICATION));
            }
        }

    }

}

