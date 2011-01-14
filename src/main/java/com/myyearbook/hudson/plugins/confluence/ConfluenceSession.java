package com.myyearbook.hudson.plugins.confluence;

import hudson.FilePath;
import hudson.plugins.jira.soap.ConfluenceSoapService;
import hudson.plugins.jira.soap.InvalidSessionException;
import hudson.plugins.jira.soap.RemoteAttachment;
import hudson.plugins.jira.soap.RemotePage;
import hudson.plugins.jira.soap.RemoteServerInfo;
import hudson.plugins.jira.soap.RemoteSpace;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.rmi.RemoteException;

public class ConfluenceSession {
    private ConfluenceSite site;
    private ConfluenceSoapService service;
    private String token;

    public ConfluenceSession(ConfluenceSite site, ConfluenceSoapService service, String token) {
	this.site = site;
	this.service = service;
	this.token = token;
    }

    public RemoteServerInfo getServerInfo() throws RemoteException {
	return this.service.getServerInfo(this.token);
    }

    public RemoteSpace getSpace(String spaceKey) throws RemoteException {
	return this.service.getSpace(this.token, spaceKey);
    }

    public RemotePage getPage(String spaceKey, String pageKey) throws RemoteException {
	return this.service.getPage(this.token, spaceKey, pageKey);
    }

    public RemoteAttachment[] getAttachments(long pageId) throws RemoteException {
	return this.service.getAttachments(this.token, pageId);
    }

    public RemoteAttachment addAttachment(long pageId, String fileName, String contentType, String comment, byte[] bytes)
	    throws RemoteException {
	RemoteAttachment attachment = new RemoteAttachment();
	attachment.setPageId(pageId);
	attachment.setFileName(fileName);
	attachment.setFileSize(bytes.length);
	attachment.setContentType(contentType);
	attachment.setComment(comment);
	return this.service.addAttachment(this.token, attachment, bytes);
    }

    public RemoteAttachment addAttachment(long pageId, FilePath file, String contentType, String comment)
	    throws IOException, InterruptedException {
	ByteArrayOutputStream baos;
	baos = new ByteArrayOutputStream((int) file.length());
	file.copyTo(baos);
	byte[] data = baos.toByteArray();
	return addAttachment(pageId, file.getName(), contentType, comment, data);
    }

    public RemoteAttachment addAttachment(long pageId, File file, String contentType, String comment)
	    throws IOException, InterruptedException {
	final byte[] buffer = new byte[8192];

	ByteArrayOutputStream baos = new ByteArrayOutputStream((int) file.length());
	FileInputStream fis = new FileInputStream(file);

	while (fis.read(buffer) >= 0) {
	    baos.write(buffer);
	}
	byte[] data = baos.toByteArray();

	return addAttachment(pageId, file.getName(), contentType, comment, data);
    }

}
