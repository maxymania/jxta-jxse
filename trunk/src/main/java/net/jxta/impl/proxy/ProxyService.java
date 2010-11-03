/*
 * Copyright (c) 2005-2007 Sun Microsystems, Inc.  All rights reserved.
 *
 *  The Sun Project JXTA(TM) Software License
 *
 *  Redistribution and use in source and binary forms, with or without 
 *  modification, are permitted provided that the following conditions are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright notice, 
 *     this list of conditions and the following disclaimer in the documentation 
 *     and/or other materials provided with the distribution.
 *
 *  3. The end-user documentation included with the redistribution, if any, must 
 *     include the following acknowledgment: "This product includes software 
 *     developed by Sun Microsystems, Inc. for JXTA(TM) technology." 
 *     Alternately, this acknowledgment may appear in the software itself, if 
 *     and wherever such third-party acknowledgments normally appear.
 *
 *  4. The names "Sun", "Sun Microsystems, Inc.", "JXTA" and "Project JXTA" must 
 *     not be used to endorse or promote products derived from this software 
 *     without prior written permission. For written permission, please contact 
 *     Project JXTA at http://www.jxta.org.
 *
 *  5. Products derived from this software may not be called "JXTA", nor may 
 *     "JXTA" appear in their name, without prior written permission of Sun.
 *
 *  THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED WARRANTIES,
 *  INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND 
 *  FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL SUN 
 *  MICROSYSTEMS OR ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, 
 *  INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT 
 *  LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, 
 *  OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF 
 *  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING 
 *  NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, 
 *  EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 *  JXTA is a registered trademark of Sun Microsystems, Inc. in the United 
 *  States and other countries.
 *
 *  Please see the license information page at :
 *  <http://www.jxta.org/project/www/license.html> for instructions on use of 
 *  the license in source files.
 *
 *  ====================================================================
 *
 *  This software consists of voluntary contributions made by many individuals 
 *  on behalf of Project JXTA. For more information on Project JXTA, please see 
 *  http://www.jxta.org.
 *
 *  This license is based on the BSD license adopted by the Apache Foundation. 
 */
package net.jxta.impl.proxy;

import net.jxta.discovery.DiscoveryEvent;
import net.jxta.discovery.DiscoveryService;
import net.jxta.document.Advertisement;
import net.jxta.document.AdvertisementFactory;
import net.jxta.endpoint.EndpointAddress;
import net.jxta.endpoint.EndpointListener;
import net.jxta.endpoint.EndpointService;
import net.jxta.endpoint.Message;
import net.jxta.endpoint.MessageElement;
import net.jxta.endpoint.StringMessageElement;
import net.jxta.exception.PeerGroupException;
import net.jxta.id.ID;
import net.jxta.id.IDFactory;
import net.jxta.impl.util.Cache;
import net.jxta.impl.util.CacheEntry;
import net.jxta.impl.util.CacheEntryListener;
import net.jxta.impl.util.LRUCache;
import net.jxta.logging.Logging;
import net.jxta.peer.PeerID;
import net.jxta.peergroup.PeerGroup;
import net.jxta.peergroup.PeerGroupID;
import net.jxta.pipe.InputPipe;
import net.jxta.pipe.OutputPipe;
import net.jxta.pipe.OutputPipeEvent;
import net.jxta.pipe.OutputPipeListener;
import net.jxta.pipe.PipeMsgEvent;
import net.jxta.pipe.PipeMsgListener;
import net.jxta.pipe.PipeService;
import net.jxta.platform.Module;
import net.jxta.protocol.DiscoveryResponseMsg;
import net.jxta.protocol.ModuleImplAdvertisement;
import net.jxta.protocol.PeerAdvertisement;
import net.jxta.protocol.PeerGroupAdvertisement;
import net.jxta.protocol.PipeAdvertisement;
import net.jxta.service.Service;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

// FIXME: jice@jxta.org - 20020515
// All public methods are synchronized.
// None of them does anything blocking so that should be about OK, however
// first it is not 100% sure, second eventhough non-blocking, some of these
// operations could take a significant amount of time, which may be unfair
// to other threads that wish to enter for a quick operation.
// Making the locking finer-grain would require significant code rework, so
// it will have to do for now.

/**
 * @since 2.6 To be removed in a future release
 */
@Deprecated
public class ProxyService implements Service, EndpointListener, PipeMsgListener, OutputPipeListener, CacheEntryListener {

    private final static Logger LOG = Logger.getLogger(ProxyService.class.getName());

    public final static int DEFAULT_THRESHOLD = 2;
    public final static int DEFAULT_LIFETIME = 1000 * 60 * 30; // 30 minutes

    /**
     * *********************************************************************
     * Define the proxy message tags
     * ********************************************************************
     */
    public static final String REQUEST_TAG = "request";
    public static final String RESPONSE_TAG = "response";

    static final String REQUESTID_TAG = "requestId";
    static final String TYPE_TAG = "type";
    static final String NAME_TAG = "name";
    static final String ID_TAG = "id";
    static final String ARG_TAG = "arg";
    static final String ATTRIBUTE_TAG = "attr";
    static final String VALUE_TAG = "value";
    static final String THRESHOLD_TAG = "threshold";
    static final String ERROR_MESSAGE_TAG = "error";
    static final String PROXYNS = "proxy";

    /**
     * *********************************************************************
     * Define the proxy request types
     * ********************************************************************
     */
    public static final String REQUEST_JOIN = "join";
    public static final String REQUEST_CREATE = "create";
    public static final String REQUEST_SEARCH = "search";
    public static final String REQUEST_LISTEN = "listen";
    public static final String REQUEST_CLOSE = "close";
    public static final String REQUEST_SEND = "send";

    /**
     * *********************************************************************
     * Define the proxy response types
     * ********************************************************************
     */
    public static final String RESPONSE_SUCCESS = "success";
    public static final String RESPONSE_ERROR = "error";
    public static final String RESPONSE_INFO = "info";
    public static final String RESPONSE_RESULT = "result";
    public static final String RESPONSE_MESSAGE = "data";

    /**
     * *********************************************************************
     * Define the proxy type tags
     * ********************************************************************
     */
    public static final String TYPE_PEER = "PEER";
    public static final String TYPE_GROUP = "GROUP";
    public static final String TYPE_PIPE = "PIPE";

    private PeerGroup group = null;
    private ID assignedID = null;
    private String serviceName = null;
    private String serviceParameter = null;
    private EndpointService endpoint = null;
    private DiscoveryService discovery = null;
    private PipeService pipe = null;
    private ModuleImplAdvertisement implAdvertisement = null;

    private final LRUCache<Integer, Requestor> searchRequests = new LRUCache<Integer, Requestor>(25); // Currently unused
    private final Map<String, PipeListenerList> pipeListeners = new TreeMap<String, PipeListenerList>();

    /**
     * Pending pipes cost only memory, so it is not a problrm to
     * wait for the GC to cleanup things. No CacheEntryListener.
     */
    private final Cache pendingPipes = new Cache(200, null);
    private Cache resolvedPipes;

    private static Map<String, PeerGroup> proxiedGroups = new HashMap<String, PeerGroup>(16);
    private static Map<String, String> passwords = new HashMap<String, String>(16);

    /**
     * {@inheritDoc}
     */
    public void init(PeerGroup group, ID assignedID, Advertisement implAdv) throws PeerGroupException {
        this.group = group;
        this.assignedID = assignedID;
        this.serviceName = assignedID.toString();
        this.implAdvertisement = (ModuleImplAdvertisement) implAdv;

        serviceParameter = group.getPeerGroupID().toString();

        // Resolved pipes cost non-memory resources, so we need to close
        // them as early as we forget them. Need a CacheEntryListener (this).
        resolvedPipes = new Cache(200, this);

        if (Logging.SHOW_CONFIG && LOG.isLoggable(Level.CONFIG)) {

            StringBuilder configInfo = new StringBuilder("Configuring JXME Proxy Service : " + assignedID);

            configInfo.append("\n\tImplementation :");
            configInfo.append("\n\t\tModule Spec ID: ").append(implAdvertisement.getModuleSpecID());
            configInfo.append("\n\t\tImpl Description : ").append(implAdvertisement.getDescription());
            configInfo.append("\n\t\tImpl URI : ").append(implAdvertisement.getUri());
            configInfo.append("\n\t\tImpl Code : ").append(implAdvertisement.getCode());
            configInfo.append("\n\tGroup Params :");
            configInfo.append("\n\t\tGroup : ").append(group.getPeerGroupName());
            configInfo.append("\n\t\tGroup ID : ").append(group.getPeerGroupID());
            configInfo.append("\n\t\tPeer ID : ").append(group.getPeerID());

            LOG.config(configInfo.toString());

        }

    }

    /**
     * {@inheritDoc}
     */
    public int startApp(String[] args) {

        Service needed = group.getEndpointService();

        if (null == needed) {

            Logging.logCheckedWarning(LOG, "Stalled until there is a endpoint service");
            return START_AGAIN_STALLED;

        }

        needed = group.getDiscoveryService();
        if (null == needed) {

            Logging.logCheckedWarning(LOG, "Stalled until there is a discovery service");
            return START_AGAIN_STALLED;

        }

        needed = group.getPipeService();
        if (null == needed) {

            Logging.logCheckedWarning(LOG, "Stalled until there is a pipe service");
            return START_AGAIN_STALLED;

        }

        endpoint = group.getEndpointService();
        discovery = group.getDiscoveryService();
        pipe = group.getPipeService();

        Logging.logCheckedFine(LOG, "addListener ", serviceName, serviceParameter);
        endpoint.addIncomingMessageListener(this, serviceName, serviceParameter);

        Logging.logCheckedInfo(LOG, "JXME Proxy Service started.");

        return Module.START_OK;
    }

    /**
     * {@inheritDoc}
     */
    public void stopApp() {

        Logging.logCheckedFine(LOG, "removeListener ", serviceName, serviceParameter);
        endpoint.removeIncomingMessageListener(serviceName, serviceParameter);

        Logging.logCheckedInfo(LOG, "JXME Proxy Service stopped.");

    }

    /**
     * {@inheritDoc}
     */
    public ModuleImplAdvertisement getImplAdvertisement() {
        return implAdvertisement;
    }

//    /**
//     * {@inheritDoc}
//     */
//    public ProxyService getInterface() {
//        return this;
//    }

    /**
     * {@inheritDoc}
     */
    public synchronized void processIncomingMessage(Message message, EndpointAddress srcAddr, EndpointAddress dstAddr) {

        logMessage(message, LOG);

        Requestor requestor = null;

        try {
            // requestor = Requestor.createRequestor(group, message, srcAddr);
            // Commented out the above line and added the following three lines.
            // The change allows to reduce the traffice going to a JXME peer
            // by able to remove ERM completly. As a side effect (severe one)
            // JXTA Proxy and JXTA relay need to be running on the same peer.
            // This changes should be pulled out as soon as ERM is implemented
            // in a more inteligent and effective way so that it doesn't
            // have any impact on JXME peers.
            EndpointAddress relayAddr = new EndpointAddress("relay", srcAddr.getProtocolAddress(), srcAddr.getServiceName(),
                    srcAddr.getServiceParameter());

            requestor = Requestor.createRequestor(group, message, relayAddr, 0);

        } catch (IOException e) {

            Logging.logCheckedWarning(LOG, "could not create requestor\n", e);

        }

        String request = popString(REQUEST_TAG, message);
        Logging.logCheckedFine(LOG, "request = ", request, " requestor ", requestor);

        if (request != null && requestor != null) {
            if (REQUEST_JOIN.equals(request)) {
                handleJoinRequest(requestor, popString(ID_TAG, message), popString(ARG_TAG, message));
            } else if (REQUEST_CREATE.equals(request)) {
                handleCreateRequest(requestor, popString(TYPE_TAG, message), popString(NAME_TAG, message),
                        popString(ID_TAG, message), popString(ARG_TAG, message));
            } else if (REQUEST_SEARCH.equals(request)) {
                handleSearchRequest(requestor, popString(TYPE_TAG, message), popString(ATTRIBUTE_TAG, message),
                        popString(VALUE_TAG, message), popString(THRESHOLD_TAG, message));
            } else if ("listen".equals(request)) {
                handleListenRequest(requestor, popString(ID_TAG, message));
            } else if ("close".equals(request)) {
                handleCloseRequest(requestor, popString(ID_TAG, message));
            } else if ("send".equals(request)) {
                handleSendRequest(requestor, popString(ID_TAG, message), message);
            }
        }
    }

    // Right now there's a security hole: passwd come in clear.
    // And not much is done for stopping clients to use the new group
    // without being authenticated. We also never get rid of these
    // additional groups.
    private synchronized void handleJoinRequest(Requestor requestor, String grpId, String passwd) {

        PeerGroup g = proxiedGroups.get(grpId);

        if (g != null) {
            if (g == this.group) {
                requestor.notifyError("Same group");
            } else if (!passwords.get(grpId).equals(passwd)) {
                requestor.notifyError("Incorrect password");
            } else {
                requestor.notifySuccess();
            }
            return;
        }

        try {
            g = group.newGroup((PeerGroupID) IDFactory.fromURI(new URI(grpId)));
            g.getRendezVousService().startRendezVous();
        } catch (Exception ge) {
            requestor.notifyError(ge.getMessage());
            return;
        }

        // XXX check membership here. (would work only for single passwd grps)
        // For now, assume join is always welcome.

        // So far so good. Try to start a proxy in that grp.
        try {
            // Fork this proxy into the new grp.
            ProxyService proxyService = new ProxyService();
            proxyService.init(g, assignedID, implAdvertisement);
            proxyService.startApp(null);
        } catch (Exception e) {
            requestor.notifyError(e.getMessage());
            return;
        }
        // set non-deft passwd
        passwords.put(grpId, passwd);
        proxiedGroups.put(grpId, g);
        requestor.notifySuccess();
    }

    private void handleCreateRequest(Requestor requestor, String type, String name, String id, String arg) {

        Logging.logCheckedFine(LOG, "handleCreateRequest type=", type, " name=", name, " id=", id, " arg=", arg);

        if (name == null) name = ""; // default name

        if (TYPE_PEER.equals(type)) {
            PeerAdvertisement adv = createPeerAdvertisement(name, id);

            if (adv != null) {

                try {

                    discovery.publish(adv);

                } catch (Exception e) {

                    Logging.logCheckedWarning(LOG, "Could not publish peer advertisement\n", e);

                }
                requestor.send(adv, RESPONSE_SUCCESS);
            } else {
                requestor.notifyError("could not create advertisement");
            }
        } else if (TYPE_GROUP.equals(type)) {
            PeerGroupAdvertisement adv = createGroupAdvertisement(name, id);

            if (adv != null) {
                requestor.send(adv, RESPONSE_SUCCESS);
            } else {
                requestor.notifyError("could not create advertisement");
            }
        } else if (TYPE_PIPE.equals(type)) {
            if (arg == null) {
                arg = PipeService.UnicastType; // default pipe type
            }

            PipeAdvertisement adv = createPipeAdvertisement(name, id, arg);

            if (adv != null) {

                try {

                    discovery.publish(adv);

                } catch (Exception e) {

                    Logging.logCheckedWarning(LOG, "Could not publish pipe advertisement\n", e);

                }

                requestor.send(adv, RESPONSE_SUCCESS);
            } else {
                requestor.notifyError("could not create advertisement");
            }
        } else {
            requestor.notifyError("unsupported type");
        }
    }

    private void handleSearchRequest(Requestor requestor, String type, String attribute, String value, String threshold) {

        Logging.logCheckedFine(LOG, "handleSearchRequest type=", type, " attribute=", attribute, " value=", value, " threshold=", threshold);

        int discoveryType;
        int thr = DEFAULT_THRESHOLD;

        try {

            thr = Integer.parseInt(threshold);

        } catch (NumberFormatException nex) {

            Logging.logCheckedWarning(LOG, "handleSearchRequest failed to parse threshold ", threshold, ", using default ", DEFAULT_THRESHOLD);

        }
        requestor.setThreshold(thr);

        if (TYPE_PEER.equals(type)) {
            discoveryType = DiscoveryService.PEER;
        } else if (TYPE_GROUP.equals(type)) {
            discoveryType = DiscoveryService.GROUP;
        } else {
            discoveryType = DiscoveryService.ADV;
        }

        Enumeration<Advertisement> each = null;

        try {
            each = discovery.getLocalAdvertisements(discoveryType, attribute, value);
        } catch (IOException e) {
            requestor.notifyError("could not search locally");
            return;
        }

        int i = 0;
        while (each.hasMoreElements() && i < thr) {
            Advertisement adv = each.nextElement();

            // notify the requestor of the result
            // FIXME this can be optimized by sending all adv's in a
            // single message
            requestor.send(adv, RESPONSE_RESULT);
            i++;
        }

        // start the query
        int queryId = discovery.getRemoteAdvertisements(null, discoveryType, attribute, value, thr);

        // register the query
        searchRequests.put(queryId, requestor);
    }

    /**
     * Finds a JXTA Pipe and starts listening to it.
     *
     * @param requestor the peer sending listen request.
     * @param id        the id of the Pipe.
     */
    private void handleListenRequest(Requestor requestor, String id) {

        Logging.logCheckedFine(LOG, "handleListenRequest id=", id);

        if (id == null) {
            requestor.notifyError("Pipe ID not specified");
            return;
        }

        PipeAdvertisement pipeAdv = findPipeAdvertisement(null, id, null);

        if (pipeAdv == null) {
            requestor.notifyError("Pipe Advertisement not found");
            return;
        }

        String pipeId = pipeAdv.getPipeID().toString();

        Logging.logCheckedFine(LOG, "listen to pipe name=", pipeAdv.getName(), " id=", pipeAdv.getPipeID(), " type=", pipeAdv.getType());

        // check to see if the input pipe already exist
        PipeListenerList list = pipeListeners.get(pipeId);

        if (list == null) {

            Logging.logCheckedFine(LOG, "first listener, create input pipe");

            // create an input pipe
            try {

                list = new PipeListenerList(pipe.createInputPipe(pipeAdv, this), pipeListeners, pipeId);

            } catch (IOException e) {

                Logging.logCheckedWarning(LOG, "could not listen to pipe\n", e);
                requestor.notifyError("could not listen to pipe");
                return;

            }

            pipeListeners.put(pipeId, list);

        }

        // add requestor to list
        list.add(requestor);

        Logging.logCheckedFine(LOG, "add requestor=", requestor, " id=", pipeId, " list=", list);
        Logging.logCheckedFine(LOG, "publish PipeAdvertisement");

        // advertise the pipe locally
        try {

            discovery.publish(pipeAdv);

        } catch (IOException e) {

            Logging.logCheckedWarning(LOG, "Could not publish pipe advertisement\n", e);

        }

        Logging.logCheckedFine(LOG, "done with listen request");

        // notify requestor of success
        requestor.notifySuccess();
    }

    private void handleCloseRequest(Requestor requestor, String id) {

        Logging.logCheckedFine(LOG, "handleCloseRequest id=", id);

        PipeListenerList list = pipeListeners.get(id);
        Logging.logCheckedFine(LOG, "handleCloseRequest list = ", list);

        if (list != null) {
            list.remove(requestor);
            if (list.size() == 0) {
                pipeListeners.remove(id);
            }
        }

        // notify requestor of success
        requestor.notifySuccess();
    }

    // Send the given message to the given pipe.
    private void sendToPipe(Requestor req, Message mess, OutputPipe out) {

        try {

            out.send(mess);
            Logging.logCheckedFine(LOG, "output pipe send end");

            // notify requestor of success
            req.notifySuccess();

        } catch (IOException e) {

            req.notifyError("could not send to pipe");
            Logging.logCheckedFine(LOG, "could not send to pipe\n", e);

        }
    }

    class ClientMessage {
        private Requestor requestor;
        private Message message;

        public ClientMessage(Requestor req, Message mess) {
            requestor = req;
            message = mess;
        }

        // Send this (pending) message
        public void send(OutputPipe out) {
            sendToPipe(requestor, message, out);
        }

    }

    class PendingPipe {
        private ClientMessage pending;

        public PendingPipe() {
            pending = null;
        }

        // Just got resolved ! Will send the pending message(s).
        public void sendPending(OutputPipe out) {
            pending.send(out);
            pending = null;
        }

        // Enqueue a new pending message.
        // (for now we only enqueue 1; others get trashed)
        public void enqueue(Requestor req, Message mess) {
            if (pending != null) {
                return;
            }
            pending = new ClientMessage(req, mess);
        }
    }

    private void handleSendRequest(Requestor requestor, String id, Message message) {

        Logging.logCheckedFine(LOG, "handleSendRequest id=", id);

        PipeAdvertisement pipeAdv = findPipeAdvertisement(null, id, null);

        if (pipeAdv == null) {
            requestor.notifyError("Could not find pipe");
            return;
        }

        String pipeId = pipeAdv.getPipeID().toString();

        Logging.logCheckedFine(LOG, "send to pipe name=", pipeAdv.getName(), " id=",
                pipeAdv.getPipeID().toString(), " arg=", pipeAdv.getType());

        // check if there are local listeners

        PipeListenerList list = pipeListeners.get(pipeId);
        Logging.logCheckedFine(LOG, "local listener list ", list);

        if (list != null && PipeService.UnicastType.equals(pipeAdv.getType())) {

            Logging.logCheckedFine(LOG, "start sending to each requestor");

            list.send(message, pipeId);
            Logging.logCheckedFine(LOG, "end sending to each requestor");

            // notify requestor of success
            requestor.notifySuccess();
            return;
        }

        // NOTE: This part is NOT exercised by the load test because all
        // clients are local. To exercise this part, comment out the
        // optimization above.

        // This is not a unicast pipe with at least one local listener
        // so we need to fingure out where the message should go.
        // This may take a while and has to be done asynchronously...
        // Carefull that the resolution can occur synchronously by this
        // very thread, and java lock will not prevent re-entry.

        Logging.logCheckedFine(LOG, "output pipe creation begin");

        // Look for the pipe in the resolved list. If not found
        // look in the pending list or add it there.
        OutputPipe out = (OutputPipe) resolvedPipes.get(pipeId);

        if (out != null) {
            sendToPipe(requestor, message, out);
            return;
        }
        PendingPipe p = (PendingPipe) pendingPipes.get(pipeId);

        if (p != null) {
            p.enqueue(requestor, message);
            return;
        }

        try {
            p = new PendingPipe();
            p.enqueue(requestor, message);
            pendingPipes.put(pipeId, p);
            pipe.createOutputPipe(pipeAdv, this);
        } catch (IOException e) {
            pendingPipes.remove(pipeId);
            requestor.notifyError("could not create output pipe");
        }
    }

    // TBD: DO WE NEED THIS FUNCTIONALITY FOR JXME?
    private PeerAdvertisement createPeerAdvertisement(String name, String id) {
        PeerAdvertisement adv = null;

        PeerID pid = null;

        if (id != null) {

            try {

                ID tempId = IDFactory.fromURI(new URI(id));
                pid = (PeerID) tempId;

            } catch (URISyntaxException e) {

                Logging.logCheckedWarning(LOG, "Could not parse peerId from url\n", e);

            } catch (ClassCastException e) {

                Logging.logCheckedWarning(LOG, "id was not a peerid\n", e);

            }
        }

        if (pid == null) pid = IDFactory.newPeerID(group.getPeerGroupID());

        Logging.logCheckedFine(LOG, "newPeerAdvertisement name=", name, " id=", pid.toString());

        try {

            // Create a pipe advertisement for this pipe.
            adv = (PeerAdvertisement) AdvertisementFactory.newAdvertisement(PeerAdvertisement.getAdvertisementType());

            adv.setPeerID(pid);
            adv.setPeerGroupID(group.getPeerGroupID());
            adv.setName(name);
            adv.setDescription("Peer Advertisement created for a jxme device");

        } catch (Exception e) {

            Logging.logCheckedWarning(LOG, "newPeerAdvertisement Exception\n", e);

        }

        return adv;
    }

    private PeerGroupAdvertisement createGroupAdvertisement(String name, String id) {
        PeerGroupAdvertisement adv;

        PeerGroupID gid = null;

        if (id != null) {

            try {

                ID tempId = IDFactory.fromURI(new URI(id));
                gid = (PeerGroupID) tempId;

            } catch (URISyntaxException e) {

                Logging.logCheckedWarning(LOG, "Invalid peergroupId\n", e);

            } catch (ClassCastException e) {

                Logging.logCheckedWarning(LOG, "id was not a peergroup id\n", e);

            }
        }

        if (gid == null) gid = IDFactory.newPeerGroupID();

        Logging.logCheckedFine(LOG, "newPeerGroupAdvertisement name=", name, " id=", gid.toString());

        adv = group.getPeerGroupAdvertisement().clone();

        try {
            // Create a PeerGroup Advertisement for this pipe.
            adv = (PeerGroupAdvertisement) AdvertisementFactory.newAdvertisement(PeerGroupAdvertisement.getAdvertisementType());
            adv.setName(name);
            adv.setPeerGroupID(gid);
            adv.setModuleSpecID(PeerGroup.allPurposePeerGroupSpecID);
            adv.setDescription("PeerGroup Advertisement created for a jxme device");
            ModuleImplAdvertisement groupImplAdv = group.getAllPurposePeerGroupImplAdvertisement();

            discovery.publish(groupImplAdv);
            discovery.publish(adv);

        } catch (Exception e) {

            Logging.logCheckedWarning(LOG, "newPeerGroupAdvertisement Exception\n", e);

        }

        return adv;
    }

    private PipeAdvertisement createPipeAdvertisement(String pipeName, String pipeId, String pipeType) {

        PipeAdvertisement adv = null;

        Logging.logCheckedFine(LOG, "newPipeAdvertisement name=", pipeName, " pipeId=", pipeId, " pipeType=", pipeType);

        if (pipeType == null || pipeType.length() == 0) {
            pipeType = PipeService.UnicastType;
        }

        if (pipeId == null) {
            pipeId = IDFactory.newPipeID(group.getPeerGroupID()).toString();
        }

        try {
            // Create a pipe advertisement for this pipe.
            adv = (PipeAdvertisement) AdvertisementFactory.newAdvertisement(PipeAdvertisement.getAdvertisementType());

            adv.setName(pipeName);
            adv.setPipeID(IDFactory.fromURI(new URI(pipeId)));
            adv.setType(pipeType);

        } catch (Exception e) {

            Logging.logCheckedWarning(LOG, "newPipeAdvertisement Exception\n", e);

        }

        return adv;
    }

    private PipeAdvertisement findPipeAdvertisement(String name, String id, String arg) {

        String attribute, value;

        Logging.logCheckedFine(LOG, "findPipeAdvertisement name=", name, " id=", id, " arg=", arg);

        if (id != null) {
            attribute = PipeAdvertisement.IdTag;
            value = id;
        } else if (name != null) {
            attribute = PipeAdvertisement.NameTag;
            value = name;
        } else {
            // the id or the name must be specified
            return null;
        }

        Enumeration<Advertisement> each;

        try {

            each = discovery.getLocalAdvertisements(DiscoveryService.ADV, attribute, value);

        } catch (IOException e) {

            Logging.logCheckedWarning(LOG, "IOException in getLocalAdvertisements()\n", e);
            return null;

        }

        PipeAdvertisement pipeAdv = null;

        while (each.hasMoreElements()) {
            Advertisement adv = each.nextElement();

            // take the first match
            if (adv instanceof PipeAdvertisement) {

                pipeAdv = (PipeAdvertisement) adv;
                Logging.logCheckedFine(LOG, "found PipeAdvertisement = ", pipeAdv);
                break;

            }
        }

        return pipeAdv;
    }

    public synchronized void discoveryEvent(DiscoveryEvent event) {

        Logging.logCheckedFine(LOG, "discoveryEvent ", event);

        Requestor requestor = searchRequests.get(event.getQueryID());
        if (requestor == null) {
            return;
        }

        DiscoveryResponseMsg response = event.getResponse();
        if (response == null) {
            return;
        }

        Enumeration<Advertisement> each = response.getAdvertisements();
        if (each == null || !each.hasMoreElements()) {
            return;
        }
        // we have a response remove it from the LRUCache
        searchRequests.remove(event.getQueryID());
        int i = 0;

        while (each.hasMoreElements() && i < requestor.getThreshold()) {

            try {

                requestor.send(each.nextElement(), RESPONSE_RESULT);

            } catch (Exception e) {

                // this should not happen unless a bad result is returned
                Logging.logCheckedWarning(LOG, "Bad result returned by DiscoveryService\n", e);

            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public synchronized void pipeMsgEvent(PipeMsgEvent event) {

        Logging.logCheckedFine(LOG, "pipeMsgEvent ", event.getPipeID());

        String id = event.getPipeID().toString();

        PipeListenerList list = pipeListeners.get(id);

        if (list != null) {

            Message message = event.getMessage();

            Logging.logCheckedFine(LOG, "pipeMsgEvent: start sending to each requestor");
            list.send(message.clone(), id);
            Logging.logCheckedFine(LOG, "pipeMsgEvent: end sending to each requestor");

        } else {

            // there are no listeners, close the input pipe
            ((InputPipe) event.getSource()).close();
            Logging.logCheckedFine(LOG, "close pipe id=", id);

        }
    }

    /**
     * {@inheritDoc}
     */
    public synchronized void outputPipeEvent(OutputPipeEvent event) {

        Logging.logCheckedFine(LOG, "outputPipeEvent ", event);

        PendingPipe p = (PendingPipe) pendingPipes.remove(event.getPipeID());

        // No one cares (anylonger). TBD should it be removed then??
        if (p == null) {
            event.getOutputPipe().close();
            return;
        }

        resolvedPipes.put(event.getPipeID(), event.getOutputPipe());
        p.sendPending(event.getOutputPipe());
    }

    private static String popString(String name, Message message) {
        MessageElement el = message.getMessageElement(PROXYNS, name);

        if (el != null) {
            message.removeMessageElement(el);
            return el.toString();
        }
        return null;
    }

    static class PipeListenerList {
        LinkedList<Requestor> list = new LinkedList<Requestor>();
        InputPipe inputPipe = null;
        Map<String, PipeListenerList> pipeListeners = null;
        String id = null;

        PipeListenerList(InputPipe inputPipe, Map<String, PipeListenerList> pipeListeners, String id) {

            this.inputPipe = inputPipe;
            this.pipeListeners = pipeListeners;
            this.id = id;

            if (pipeListeners != null) {
                Logging.logCheckedConfig(LOG, "number of pipeListeners = ", pipeListeners.size());
            }

        }

        void add(Requestor requestor) {

            Logging.logCheckedInfo(LOG, "add ", requestor, " from ", this);

            if (!list.contains(requestor)) {
                Logging.logCheckedFine(LOG, "requestor add");
                list.add(requestor);
            } else {
                Logging.logCheckedFine(LOG, "requestor exits already");
            }

        }

        void remove(Requestor requestor) {

            Logging.logCheckedInfo(LOG, "remove ", requestor, " from ", this);

            Logging.logCheckedFine(LOG, "removed = ", list.remove(requestor));

            if (list.size() == 0) {
                // close the pipe and remove from the listenerList
                if (inputPipe != null) {
                    inputPipe.close();
                }

                if (id != null && pipeListeners != null) {
                    pipeListeners.remove(id);
                }
            }
        }

        int size() {

            int size = list.size();
            Logging.logCheckedFine(LOG, "size ", size);

            return size;
        }

        private static StringMessageElement sme = new StringMessageElement(RESPONSE_TAG, RESPONSE_MESSAGE, null);

        void send(Message message, String id) {
            LOG.fine("send list.size = " + list.size());

            message.addMessageElement(PROXYNS, sme);
            message.addMessageElement(PROXYNS, new StringMessageElement(ID_TAG, id, null));

            // removed all element that are known to be not needed
            Iterator<MessageElement> elements = message.getMessageElements();

            while (elements.hasNext()) {
                MessageElement el = elements.next();
                String name = el.getElementName();

                if (name.startsWith("RendezVousPropagate")) {

                    Logging.logCheckedFine(LOG, "removeMessageElement ", name);
                    elements.remove();

                } else if (name.startsWith("JxtaWireHeader")) {

                    Logging.logCheckedFine(LOG, "removeMessageElement ", name);
                    elements.remove();

                } else if (name.startsWith("RdvIncarnjxta")) {

                    Logging.logCheckedFine(LOG, "removeMessageElement ", name);
                    elements.remove();

                } else if (name.startsWith("JxtaEndpointRouter")) {

                    Logging.logCheckedFine(LOG, "removeMessageElement ", name);
                    elements.remove();

                } else if (name.startsWith("EndpointRouterMsg")) {

                    Logging.logCheckedFine(LOG, "removeMessageElement ", name);
                    elements.remove();

                } else if (name.startsWith("EndpointHeaderSrcPeer")) {

                    Logging.logCheckedFine(LOG, "removeMessageElement ", name);
                    elements.remove();

                }
            }

            Iterator<Requestor> iterator = list.iterator();
            try {
                while (iterator.hasNext()) {
                    Requestor requestor = iterator.next();

                    if (!requestor.send(message.clone())) {
                        // could not send to listener, remove them from the list
                        remove(requestor);
                    }
                }
            } catch (Exception ex) {

                Logging.logCheckedFine(LOG, "Error sending", ex);

            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return "PipeListenerList size=" + list.size();
        }
    }

    protected static void logMessage(Message message, Logger log) {

        if (!Logging.SHOW_FINER || !log.isLoggable(Level.FINER)) {
            return;
        }

        StringBuilder out = new StringBuilder("\n**************** begin ****************\n");

        Message.ElementIterator elements = message.getMessageElements();

        while (elements.hasNext()) {
            MessageElement element = elements.next();

            out.append("[").append(elements.getNamespace()).append(",").append(element.getElementName()).append("]=").append(element.toString()).append(
                    "\n");
        }

        out.append("****************  end  ****************\n");
        log.finer(out.toString());
    }

    /**
     * {@inheritDoc}
     */
    public void purged(CacheEntry ce) {
        // A resolved pipe was purged from the cache because we have to
        // many pre-resolved pipes hanging around. Close it, because
        // it may be holding critical resources that the GC will not be
        // sensitive to.
        ((OutputPipe) (ce.getValue())).close();
    }
}
