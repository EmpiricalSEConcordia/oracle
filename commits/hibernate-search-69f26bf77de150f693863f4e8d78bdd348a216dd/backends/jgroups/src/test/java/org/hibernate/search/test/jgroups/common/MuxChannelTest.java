/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.test.jgroups.common;

import java.util.Random;

import org.hibernate.cfg.Configuration;
import org.hibernate.search.cfg.Environment;
import org.hibernate.search.backend.jgroups.impl.DispatchMessageSender;
import org.hibernate.search.backend.jgroups.impl.MessageSenderService;
import org.hibernate.search.spi.SearchFactoryIntegrator;
import org.hibernate.search.util.configuration.impl.ConfigurationParseHelper;
import org.jgroups.JChannel;
import org.jgroups.blocks.mux.MuxUpHandler;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Test injected mux supported channel.
 *
 * @author Ales Justin
 */
public abstract class MuxChannelTest extends JGroupsCommonTest {

	private JChannel[] channels;
	private short muxId;

	@Test
	public void testMuxDispatcher() throws Exception {
		SearchFactoryIntegrator searchFactory = getSearchFactory().unwrap( SearchFactoryIntegrator.class );
		MessageSenderService sender = searchFactory.getServiceManager().requestService( MessageSenderService.class );
		Assert.assertNotNull( sender );
		String className = sender.getClass().getName();
		Assert.assertTrue( "Wrong sender instance: " + className, className.contains( "DispatchMessageSender" ) );
	}

	@Override
	@Before
	public void setUp() throws Exception {
		muxId = (short) new Random().nextInt();
		channels = createChannels();
		forceConfigurationRebuild();
		super.setUp();
	}

	@Override
	@After
	public void tearDown() throws Exception {
		try {
			super.tearDown();
		}
		finally {
			for ( JChannel channel : channels ) {
				if ( channel != null ) {
					channel.close();
				}
			}
		}
	}

	@Override
	protected void configure(Configuration cfg) {
		super.configure( cfg );
		cfg.setProperty( "hibernate.search.default." + Environment.WORKER_BACKEND, getMasterBackend() );
		cfg.getProperties().put( DispatchMessageSender.CHANNEL_INJECT, channels[0] );
		cfg.getProperties().put( DispatchMessageSender.MUX_ID, muxId );
	}

	@Override
	protected void commonConfigure(Configuration cfg) {
		super.commonConfigure( cfg );
		cfg.setProperty( "hibernate.search.default." + Environment.WORKER_BACKEND, getSlaveBackend() );
		cfg.getProperties().put( DispatchMessageSender.CHANNEL_INJECT, channels[1] );
		cfg.getProperties().put( DispatchMessageSender.MUX_ID, muxId );
	}

	protected JChannel createChannel() throws Exception {
		JChannel channel = new JChannel( ConfigurationParseHelper.locateConfig( JGroupsCommonTest.TESTING_JGROUPS_CONFIGURATION_FILE ) );
		channel.setUpHandler( new MuxUpHandler() );
		channel.connect( "JGroupsCommonTest" + JGroupsCommonTest.CHANNEL_NAME );
		return channel;
	}

	protected abstract String getMasterBackend();

	protected abstract JChannel[] createChannels() throws Exception;

	protected abstract String getSlaveBackend();

}
