package net.jxse.systemtests.colocated;

import java.net.URI;

import net.jxta.platform.NetworkConfigurator;
import net.jxta.platform.NetworkManager;
import net.jxta.platform.NetworkManager.ConfigMode;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class RelayedHttpCommsTest {

	@Rule
    public TemporaryFolder tempStorage = new TemporaryFolder();

    private NetworkManager aliceManager;
    private NetworkManager bobManager;
    private NetworkManager relayManager;
	
    @Before
    public void initPeers() throws Exception {
    	URI relayURI = URI.create("http://127.0.0.1:50000");
    	relayManager = configureRelay("relay", "127.0.0.1", 50000);
    	
        aliceManager = configurePeer("alice", relayURI);
        bobManager = configurePeer("bob", relayURI);

        relayManager.startNetwork();
        aliceManager.startNetwork();
        bobManager.startNetwork();
    }

    private NetworkManager configureRelay(String relayName, String interfaceAddr, int port) throws Exception {
		NetworkManager manager = new NetworkManager(ConfigMode.RENDEZVOUS_RELAY, relayName, tempStorage.newFolder(relayName).toURI());
		NetworkConfigurator configurator = manager.getConfigurator();
		configurator.setUseMulticast(false);
		configurator.setUseOnlyRelaySeeds(true);
		configurator.setUseOnlyRendezvousSeeds(true);
		
		configurator.setTcpEnabled(false);
		configurator.setHttp2Enabled(false);

		configurator.setHttpEnabled(true);
		configurator.setHttpIncoming(true);
		configurator.setHttp2Outgoing(true);
		configurator.setHttpInterfaceAddress(interfaceAddr);
		configurator.setHttpPort(port);
		
		return manager;
	}

	private NetworkManager configurePeer(String peerName, URI relayRdvURI) throws Exception {
    	NetworkManager manager = new NetworkManager(ConfigMode.EDGE, peerName, tempStorage.newFolder(peerName).toURI());
    	NetworkConfigurator configurator = manager.getConfigurator();
    	configurator.setUseMulticast(false);
    	configurator.setUseOnlyRelaySeeds(true);
    	configurator.setUseOnlyRendezvousSeeds(true);
    	
    	configurator.setTcpEnabled(false);
    	configurator.setHttp2Enabled(false);
    	
    	configurator.setHttpEnabled(true);
    	configurator.setHttpIncoming(false);
    	configurator.setHttpOutgoing(true);
    	
    	configurator.addSeedRelay(relayRdvURI);
    	configurator.addSeedRendezvous(relayRdvURI);
    	return manager;
	}

	@After
    public void killPeers() throws Exception {
        aliceManager.stopNetwork();
        bobManager.stopNetwork();
        relayManager.stopNetwork();
    }
	
	@Test(timeout=30000)
	public void testComms() throws Exception {
		SystemTestUtils.testPeerCommunication(aliceManager, bobManager);
	}
}
