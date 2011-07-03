
package com.myyearbook.hudson.plugins.confluence;

import hudson.FilePath;
import hudson.plugins.confluence.soap.ConfluenceSoapService;
import hudson.plugins.confluence.soap.RemoteAttachment;
import hudson.plugins.confluence.soap.RemotePage;
import hudson.plugins.confluence.soap.RemotePageUpdateOptions;
import hudson.plugins.confluence.soap.RemoteServerInfo;
import hudson.plugins.confluence.soap.RemoteSpace;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.rmi.RemoteException;

/**
 * Connection to Confluence
 * 
 * @author Joe Hansche <jhansche@myyearbook.com>
 */
public class ConfluenceSession {
    /**
     * Confluence SOAP service
     */
    private final ConfluenceSoapService service;

    /**
     * Authentication token, obtained from
     * {@link ConfluenceSoapService#login(String,String)}
     */
    private final String token;

    /**
     * Constructor
     * 
     * @param service
     * @param token
     */
    /* package */ConfluenceSession(final ConfluenceSoapService service, final String token) {
        this.service = service;
        this.token = token;
    }

    /**
     * Get server info
     * 
     * @return {@link RemoteServerInfo} instance
     * @throws RemoteException
     */
    public RemoteServerInfo getServerInfo() throws RemoteException {
        return this.service.getServerInfo(this.token);
    }

    /**
     * Get a Space by key name
     * 
     * @param spaceKey
     * @return {@link RemoteSpace} instance
     * @throws RemoteException
     */
    public RemoteSpace getSpace(String spaceKey) throws RemoteException {
        return this.service.getSpace(this.token, spaceKey);
    }

    /**
     * Get a Page by Space and Page key names
     * 
     * @param spaceKey
     * @param pageKey
     * @return {@link RemotePage} instance
     * @throws RemoteException
     */
    public RemotePage getPage(String spaceKey, String pageKey) throws RemoteException {
        return this.service.getPage(this.token, spaceKey, pageKey);
    }

    public RemotePage storePage(final RemotePage page) throws RemoteException {
        return this.service.storePage(this.token, page);
    }

    public RemotePage updatePage(final RemotePage page, final RemotePageUpdateOptions options)
            throws RemoteException {
        return this.service.updatePage(this.token, page, options);
    }

    /**
     * Get all attachments for a page
     * 
     * @param pageId
     * @return Array of {@link RemoteAttachment}s
     * @throws RemoteException
     */
    public RemoteAttachment[] getAttachments(long pageId) throws RemoteException {
        return this.service.getAttachments(this.token, pageId);
    }

    /**
     * Attach the file
     * 
     * @param pageId
     * @param fileName
     * @param contentType
     * @param comment
     * @param bytes
     * @return {@link RemoteAttachment} instance that was created on the server
     * @throws RemoteException
     */
    public RemoteAttachment addAttachment(long pageId, String fileName, String contentType,
            String comment, byte[] bytes)
            throws RemoteException {
        RemoteAttachment attachment = new RemoteAttachment();
        attachment.setPageId(pageId);
        attachment.setFileName(sanitizeFileName(fileName));
        attachment.setFileSize(bytes.length);
        attachment.setContentType(contentType);
        attachment.setComment(comment);
        return this.service.addAttachment(this.token, attachment, bytes);
    }

    /**
     * Attach the file
     * 
     * @param pageId
     * @param file
     * @param contentType
     * @param comment
     * @return {@link RemoteAttachment} instance
     * @throws IOException
     * @throws InterruptedException
     */
    public RemoteAttachment addAttachment(long pageId, FilePath file, String contentType,
            String comment)
            throws IOException, InterruptedException {
        ByteArrayOutputStream baos;
        baos = new ByteArrayOutputStream((int) file.length());
        file.copyTo(baos);
        byte[] data = baos.toByteArray();
        return addAttachment(pageId, file.getName(), contentType, comment, data);
    }

    /**
     * Attach the file
     * 
     * @param pageId
     * @param file
     * @param contentType
     * @param comment
     * @return {@link RemoteAttachment} instance
     * @throws IOException
     * @throws FileNotFoundException
     */
    public RemoteAttachment addAttachment(long pageId, File file, String contentType, String comment)
            throws FileNotFoundException, IOException {
        final int len = (int) file.length();

        final FileChannel in = new FileInputStream(file).getChannel();

        final ByteArrayOutputStream baos = new ByteArrayOutputStream(len);
        final WritableByteChannel out = Channels.newChannel(baos);

        try {
            // Copy
            in.transferTo(0, len, out);
        } finally {
            // Clean up
            out.close();
            in.close();
        }

        final byte[] data = baos.toByteArray();

        return addAttachment(pageId, file.getName(), contentType, comment, data);
    }

    /**
     * Sanitize the attached filename, per Confluence restrictions
     * 
     * @param fileName
     * @return
     */
    public static String sanitizeFileName(String fileName) {
        if (fileName == null) {
            return null;
        }
        return hudson.Util.fixEmptyAndTrim(fileName.replace('+', '_').replace('&', '_'));
    }
}
