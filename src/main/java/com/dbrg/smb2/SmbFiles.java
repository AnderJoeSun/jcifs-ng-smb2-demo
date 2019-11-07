package com.dbrg.smb2;


import java.io.*;
import java.net.MalformedURLException;
import jcifs.CIFSContext;
import jcifs.CIFSException;
import jcifs.SmbResource;
import jcifs.context.SingletonContext;
import jcifs.smb.NtlmPasswordAuthenticator;
import jcifs.smb.SmbFile;
import lombok.extern.slf4j.Slf4j;


/**
 * @author wangying
 */
@Slf4j
public class SmbFiles {
    private static final String SMB_PROTOCOL = "smb://";
    private String username;
    private String password;

    private SmbFiles(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public static SmbFiles getInstance(String username, String password) {
        Holder.username = username;
        Holder.password = password;
        return Holder.INSTANCE;
    }

    private String smb(String path) {
        if (StringUtil.isNotEmpty(path) && !path.startsWith(SMB_PROTOCOL)) {
            path = StringUtil.transBlank(SMB_PROTOCOL + path);
        }
        return path;
    }

    /**
     * @param source
     * @param target
     */
    public void renameFile(String source, String target) {
        try (SmbFile sourceFile = newSmbFile(source);
             SmbFile targetFile = newSmbFile(target)) {
            renameTo(sourceFile, targetFile);
        } catch (CIFSException | MalformedURLException e) {
            log.warn("Rename smbFile met exception, error info: {}", e);
        }
    }

    /**
     * @param file
     * @return
     */
    public String getSmbFileName(SmbFile file) {
        return file.getName();
    }

    /**
     * @param source
     * @param target
     */
    public void moveSmbFileToSmb(SmbResource source, String target) throws CIFSException,
            MalformedURLException {
        boolean renamed = false;
        try (SmbFile targetFile = newSmbFile(target)) {
            if (targetFile.exists()) {
                targetFile.delete();
            }
            targetFile.createNewFile();
            try {
                source.copyTo(targetFile);
                if (targetFile.exists()) {
                    renamed = true;
                }
            } finally {
                if (renamed && source.exists()) {
                    source.delete();
                }
            }
        }
    }

    /**
     * @param filePath
     * @return
     * @throws CIFSException
     * @throws MalformedURLException
     */
    public SmbFile createFile(String filePath) throws CIFSException, MalformedURLException {
        try (SmbFile smbFile = newSmbFile(filePath)) {
            if (!smbFile.exists()) {
                smbFile.createNewFile();
            }
            return smbFile;
        }
    }

    /**
     * Write {@link SmbFile} to {@link SmbFile}
     *
     * @param source
     * @param target
     * @return
     * @throws IOException
     */
    public void writeSmbFile(String source, String target) throws IOException {
        if (StringUtil.isEmpty(source) || StringUtil.isEmpty(target)) {
            return;
        }
        SmbFile smbFile = newSmbFile(source);
        writeSmbFile(smbFile.getInputStream(), target);
    }

    /**
     * Write {@link SmbFile} or {@link File} to {@link SmbFile}
     *
     * @param in
     * @param target
     * @return
     * @throws IOException
     */
    public void writeSmbFile(InputStream in, String target) throws IOException {
        if (null != in && StringUtil.isNotEmpty(target)) {
            try (SmbFile file = newSmbFile(target)) {
                if (!file.exists()) {
                    file.createNewFile();
                }
                write(in, file);
            }
        }
    }

    private void write(InputStream in, SmbFile file) throws IOException {
        try (OutputStream os = file.getOutputStream()) {
            byte[] bytes = new byte[1024];
            while (in.read(bytes) != -1) {
                os.write(bytes);
            }
            in.close();
        }
    }

    public SmbFile createDirectory(String targetDir) throws MalformedURLException,
            CIFSException {
        try (SmbFile dir = newSmbFile(targetDir)) {
            dir.mkdir();
            return dir;
        }
    }

    public void cleanupDirectory(SmbResource smbResource) throws CIFSException {
        if (smbResource != null) {
            smbResource.delete();
        }
    }

    public InputStream readSmbFile(String path) throws IOException {
        try (SmbFile file = newSmbFile(path)) {
            if (null == file || !file.exists()) {
                throw new FileNotFoundException(path);
            }
            return file.getInputStream();
        }
    }

    public String getSmbUncPath(String fileAbsolutePath) throws MalformedURLException {
        try (SmbFile smbFile = newSmbFile(fileAbsolutePath)) {
            return smbFile.getCanonicalUncPath();
        }
    }

    public SmbFile newSmbFile(String path) throws MalformedURLException {
        return new SmbFile(smb(path), build(username, password));
    }

    public void renameTo(SmbResource sourceFile, SmbResource targetFile) throws CIFSException {
        boolean renamed = false;
        try {
            sourceFile.renameTo(targetFile);
            if (targetFile.exists()) {
                renamed = true;
            }
        } finally {
            if (renamed && sourceFile.exists()) {
                sourceFile.delete();
            }
        }
    }

    public void renameDirectory(String source, String target) throws CIFSException, MalformedURLException {
        try (SmbResource srcDir = newSmbFile(source);
             SmbResource targetDir = createDirectory(target)) {
            targetDir.mkdir();
            renameTo(srcDir, targetDir);
        }
    }

    private CIFSContext build(String username, String password) {
        return SingletonContext.getInstance().withCredentials(new NtlmPasswordAuthenticator(username, password));
    }

    static class Holder {
        private static String username;
        private static String password;
        private static SmbFiles INSTANCE = new SmbFiles(username, password);
    }

}