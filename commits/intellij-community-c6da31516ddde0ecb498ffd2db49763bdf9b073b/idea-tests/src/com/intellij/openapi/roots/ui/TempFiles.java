package com.intellij.openapi.roots.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;

public class TempFiles {
  private Collection<File> myFilesToDelete;

  public TempFiles(Collection<File> filesToDelete) {
    myFilesToDelete = filesToDelete;
  }

  public VirtualFile createVFile(String prefix) throws IOException {
    return getVFileByFile(createTempFile(prefix));
  }

  public VirtualFile createVFile(String prefix, String postfix) throws IOException {
    return getVFileByFile(createTempFile(prefix, postfix));
  }

  public File createTempFile(String prefix) throws IOException {
    return createTempFile(prefix, "_Temp_File_");
  }

  public File createTempFile(String prefix, String postfix) throws IOException {
    File tempFile = File.createTempFile(prefix, postfix);
    tempFileCreated(tempFile);
    return tempFile;
  }

  private void tempFileCreated(File tempFile) {
    myFilesToDelete.add(tempFile);
    tempFile.deleteOnExit();
  }

  public static VirtualFile getVFileByFile(File tempFile) {
    refreshVfs();
    return LocalFileSystem.getInstance().findFileByIoFile(tempFile);
  }

  public static void refreshVfs() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        VirtualFileManager.getInstance().refresh(false);
      }
    });
  }

  public File createTempDir() throws IOException {
    return createTempDir("dir");
  }

  private File createTempDir(String prefix) throws IOException {
    File dir = FileUtil.createTempDirectory(prefix, "test");
    tempFileCreated(dir);
    return dir;
  }

  public VirtualFile createTempVDir() throws IOException {
    return createTempVDir("dir");
  }

  public VirtualFile createTempVDir(String prefix) throws IOException {
    return getVFileByFile(createTempDir(prefix));
  }

  public File createTempSubDir(File content) {
    File subDir = new File(content, "source");
    if (!subDir.mkdir()) throw new RuntimeException(subDir.toString());
    tempFileCreated(subDir);
    return subDir;
  }

  public String createTempPath() throws IOException {
    File tempFile = createTempFile("xxx");
    String absolutePath = tempFile.getAbsolutePath();
    tempFile.delete();
    return absolutePath;
  }

  public void deleteAll() {
    for (Iterator<File> iterator = myFilesToDelete.iterator(); iterator.hasNext();) {
      File file = iterator.next();
      file.delete();
    }
  }

  public VirtualFile createVFile(VirtualFile parentDir, String name, String text) throws IOException {
    final VirtualFile virtualFile = parentDir.createChildData(this, name);
    VfsUtil.saveText(virtualFile, text + "\n");
    return virtualFile;
  }
}
