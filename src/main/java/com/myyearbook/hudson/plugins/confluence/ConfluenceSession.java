package com.myyearbook.hudson.plugins.confluence;

import hudson.FilePath;
import hudson.plugins.jira.soap.ConfluenceSoapService;
import hudson.plugins.jira.soap.RemoteAttachment;
import hudson.plugins.jira.soap.RemotePage;
import hudson.plugins.jira.soap.RemoteServerInfo;
import hudson.plugins.jira.soap.RemoteSpace;

import java.io.ByteArrayOutputStream;
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

    public RemoteAttachment addAttachment(long pageId, String fileName, byte[] bytes) throws RemoteException {
	RemoteAttachment attachment = new RemoteAttachment();
	attachment.setPageId(pageId);
	attachment.setFileName(fileName);
	attachment.setFileSize(bytes.length);
	return this.service.addAttachment(this.token, attachment, bytes);
    }

    public RemoteAttachment addAttachment(long pageId, FilePath file) throws IOException, InterruptedException {
	ByteArrayOutputStream baos;
	baos = new ByteArrayOutputStream((int) file.length());
	file.copyTo(baos);
	byte[] data = baos.toByteArray();
	return addAttachment(pageId, file.getBaseName(), data);
    }
}
