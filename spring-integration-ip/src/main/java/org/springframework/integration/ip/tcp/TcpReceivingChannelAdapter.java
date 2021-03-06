/*
 * Copyright 2002-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.integration.ip.tcp;

import java.util.concurrent.ScheduledFuture;

import org.springframework.integration.Message;
import org.springframework.integration.endpoint.MessageProducerSupport;
import org.springframework.integration.ip.tcp.connection.AbstractClientConnectionFactory;
import org.springframework.integration.ip.tcp.connection.AbstractConnectionFactory;
import org.springframework.integration.ip.tcp.connection.AbstractServerConnectionFactory;
import org.springframework.integration.ip.tcp.connection.ClientModeCapable;
import org.springframework.integration.ip.tcp.connection.ClientModeConnectionManager;
import org.springframework.integration.ip.tcp.connection.ConnectionFactory;
import org.springframework.integration.ip.tcp.connection.TcpListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.Assert;

/**
 * Tcp inbound channel adapter using a TcpConnection to 
 * receive data - if the connection factory is a server
 * factory, this Listener owns the connections. If it is
 * a client factory, the sender owns the connection.
 * 
 * @author Gary Russell
 * @since 2.0
 *
 */
public class TcpReceivingChannelAdapter 
	extends MessageProducerSupport implements TcpListener, ClientModeCapable {

	private AbstractConnectionFactory clientConnectionFactory;

	private AbstractConnectionFactory serverConnectionFactory;

	private volatile boolean isClientMode;

	private volatile TaskScheduler scheduler;

	private volatile long retryInterval = 60000;

	private volatile ScheduledFuture<?> scheduledFuture;

	private volatile ClientModeConnectionManager clientModeConnectionManager;

	private volatile boolean active;

	public boolean onMessage(Message<?> message) {
		sendMessage(message);
		return false;
	}

	@Override
	protected void onInit() {
		super.onInit();
		if (this.isClientMode) {
			Assert.notNull(this.clientConnectionFactory,
					"For client-mode, connection factory must be type='client'");
			Assert.isTrue(!this.clientConnectionFactory.isSingleUse(),
					"For client-mode, connection factory must have single-use='false'");
		}
	}

	@Override // protected by super#lifecycleLock
	protected void doStart() {
		super.doStart();
		if (!this.active) {
			this.active = true;
			if (this.serverConnectionFactory != null) {
				this.serverConnectionFactory.start();
			}
			if (this.clientConnectionFactory != null) {
				this.clientConnectionFactory.start();
			}
			if (this.isClientMode) {
				ClientModeConnectionManager manager = new ClientModeConnectionManager(
						this.clientConnectionFactory);
				this.clientModeConnectionManager = manager;
				this.scheduledFuture = this.getScheduler().scheduleAtFixedRate(manager, this.retryInterval);
			}
		}
	}

	@Override // protected by super#lifecycleLock
	protected void doStop() {
		super.doStop();
		if (this.active) {
			this.active = false;
			if (this.scheduledFuture != null) {
				this.scheduledFuture.cancel(true);
			}
			this.clientModeConnectionManager = null;
			if (this.clientConnectionFactory != null) {
				this.clientConnectionFactory.stop();
			}
			if (this.serverConnectionFactory != null) {
				this.serverConnectionFactory.stop();
			}
		}
	}

	/**
	 * Sets the client or server connection factory; for this (an inbound adapter), if
	 * the factory is a client connection factory, the sockets are owned by a sending
	 * channel adapter and this adapter is used to receive replies.
	 * 
	 * @param connectionFactory the connectionFactory to set
	 */
	public void setConnectionFactory(AbstractConnectionFactory connectionFactory) {
		if (connectionFactory instanceof AbstractClientConnectionFactory) {
			this.clientConnectionFactory = connectionFactory;
		} else {
			this.serverConnectionFactory = connectionFactory;
		}
		connectionFactory.registerListener(this);		
	}

	public boolean isListening() {
		if (this.serverConnectionFactory == null) {
			return false;
		}
		if (this.serverConnectionFactory instanceof AbstractServerConnectionFactory) {
			return ((AbstractServerConnectionFactory) this.serverConnectionFactory).isListening();
		}
		return false;
	}

	public String getComponentType(){
		return "ip:tcp-inbound-channel-adapter";
	}

	/**
	 * @return the clientConnectionFactory
	 */
	protected ConnectionFactory getClientConnectionFactory() {
		return clientConnectionFactory;
	}

	/**
	 * @return the serverConnectionFactory
	 */
	protected ConnectionFactory getServerConnectionFactory() {
		return serverConnectionFactory;
	}

	/**
	 * @return the isClientMode
	 */
	public boolean isClientMode() {
		return this.isClientMode;
	}

	/**
	 * @param isClientMode
	 *            the isClientMode to set
	 */
	public void setClientMode(boolean isClientMode) {
		this.isClientMode = isClientMode;
	}

	/**
	 * @return the scheduler
	 */
	protected TaskScheduler getScheduler() {
		if (this.scheduler == null) {
			ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
			scheduler.initialize();
			this.scheduler = scheduler;
		}
		return this.scheduler;
	}

	/**
	 * @param scheduler
	 *            the scheduler to set
	 */
	public void setScheduler(TaskScheduler scheduler) {
		this.scheduler = scheduler;
	}

	/**
	 * @return the retryInterval
	 */
	public long getRetryInterval() {
		return this.retryInterval;
	}

	/**
	 * @param retryInterval
	 *            the retryInterval to set
	 */
	public void setRetryInterval(long retryInterval) {
		this.retryInterval = retryInterval;
	}

	public boolean isClientModeConnected() {
		if (this.isClientMode && this.clientModeConnectionManager != null) {
			return this.clientModeConnectionManager.isConnected();
		} else {
			return false;
		}
	}

	public void retryConnection() {
		if (this.active && this.isClientMode && this.clientModeConnectionManager != null) {
			this.clientModeConnectionManager.run();
		}
	}

}
