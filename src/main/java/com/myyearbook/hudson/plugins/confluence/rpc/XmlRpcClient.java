/*
 * Copyright 2011-2012 MeetMe, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.myyearbook.hudson.plugins.confluence.rpc;

import java.rmi.RemoteException;

import javax.xml.rpc.ServiceException;

public class XmlRpcClient {
    protected XmlRpcClient(String url) {
    }

    public static jenkins.plugins.confluence.soap.v1.ConfluenceSoapService getInstance(String url)
            throws RemoteException {
        try {
            final jenkins.plugins.confluence.soap.v1.ConfluenceSoapServiceServiceLocator locator = new jenkins.plugins.confluence.soap.v1.ConfluenceSoapServiceServiceLocator();
            locator.setConfluenceserviceV1EndpointAddress(url);
            return locator.getConfluenceserviceV1();
        } catch (ServiceException e) {
            throw new RemoteException("Failed to create SOAP Client", e);
        }
    }

    public static jenkins.plugins.confluence.soap.v2.ConfluenceSoapService getV2Instance(String url)
            throws RemoteException {
        try {
            final jenkins.plugins.confluence.soap.v2.ConfluenceSoapServiceServiceLocator locator = new jenkins.plugins.confluence.soap.v2.ConfluenceSoapServiceServiceLocator();
            locator.setConfluenceserviceV2EndpointAddress(url);
            return locator.getConfluenceserviceV2();
        } catch (ServiceException e) {
            throw new RemoteException("Failed to create SOAP Client", e);
        }
    }
}
