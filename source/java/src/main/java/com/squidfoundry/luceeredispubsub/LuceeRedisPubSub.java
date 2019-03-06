package com.squidfoundry.luceeredispubsub;

import java.util.Map;

import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.io.IOException;

import lucee.runtime.gateway.Gateway;
import lucee.runtime.gateway.GatewayEngine;
import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.runtime.type.Struct;
import lucee.runtime.util.Creation;

public class LuceeRedisPubSub extends JedisPubSub implements Gateway {
	
	private JedisPool pool;
	
	private CFMLEngine cfmlEngine;
	private Creation creator;
	
	public static final String DEFAULT_HOST = "127.0.0.1";
	public static final int DEFAULT_PORT = 6379;
	public static final String DEFAULT_CHANNEL = "luceeredis";
	
	protected String host = DEFAULT_HOST;
	protected int port = DEFAULT_PORT;
	protected String channel = DEFAULT_CHANNEL;
	protected String auth;
	
	private String id;
	private int state=Gateway.STOPPED;
	private String cfcPath;
	private GatewayEngine engine;

	public void init(GatewayEngine engine, String id, String cfcPath, Map <String, String>config) {
		this.engine=engine;
		this.cfcPath=cfcPath;
		this.id=id;
		
		cfmlEngine=CFMLEngineFactory.getInstance();
		creator = cfmlEngine.getCreationUtil();
		
		if (config.containsKey("host"))
			host = config.get("host").toString();
		if (config.containsKey("port"))
			port = Integer.parseInt(config.get("port").toString());
		if (config.containsKey("auth"))
			auth = config.get("auth").toString();
		if (config.containsKey("channel"))
			channel = config.get("channel").toString();
		if (config.containsKey("cfcPath"))
			this.cfcPath = config.get("cfcPath").toString();
		
		engine.log(this,GatewayEngine.LOGLEVEL_INFO,"initializing");
		engine.log(this,GatewayEngine.LOGLEVEL_INFO,"RedisSubGateway(" + getId() + ") configured for " + host
				+ ":" + port + "::" + channel + ".");
	}

	public String sendMessage(Map<?, ?> _data) {
		String status="OK";
		engine.log(this, GatewayEngine.LOGLEVEL_INFO, "Message from gateway was:" + _data.get("message").toString());

		try(Jedis redisClient = pool.getResource()) {
			redisClient.publish(channel, _data.get("message").toString());
		} catch(JedisConnectionException e) {
			doStart();
		}
		
		return status;
	}

	public void doStart() {
		state = STARTING;
		engine.log(this,GatewayEngine.LOGLEVEL_INFO,"started");
		final LuceeRedisPubSub self = this;

		new Thread(new Runnable() {
			public void run() {
				try {
					startJedis();
				} catch(Exception e) {
					engine.log(self, GatewayEngine.LOGLEVEL_ERROR, e.getMessage());
				}
			}
		}).start();
		
		state = RUNNING;
		engine.log(this,GatewayEngine.LOGLEVEL_INFO,"running");
		
	}

	public void doRestart() {

	}

	public void doStop() {
		
	}

	public Object getHelper() {
		return null;
	}

	public String getId() {
		return id;
	}
	
	public int getState() {
		return state;
	 }
	
	protected void startJedis() throws IOException {		
		if(pool == null) {
			pool = new JedisPool(getJedisPoolConfig(), host, port, 2000);
		}
		
		try(Jedis redisClient = pool.getResource()) {
			redisClient.subscribe(this, channel);
		} catch(Exception e) {
			System.out.println("There was an error subscribing");
			engine.log(this, GatewayEngine.LOGLEVEL_ERROR, e.getMessage());
		}

	}

	protected JedisPoolConfig getJedisPoolConfig() throws IOException {
		JedisPoolConfig config = new JedisPoolConfig();
		return config;
	}

	@Override
	public void onMessage(String channel, String message) {
		engine.log(this, GatewayEngine.LOGLEVEL_INFO, "RedisSubGateway(" + getId()
				+ ") Message received. Message was: '"
				+ message.substring(0, Math.min(20, message.length())) + "'");
		
		Struct data=creator.createStruct();
        	data.setEL(creator.createKey("message"), message);
        	data.setEL(creator.createKey("cfcMethod"), "onIncomingMessage");
        	data.setEL(creator.createKey("cfcTimeout"), new Double(10));
        	data.setEL(creator.createKey("cfcPath"), this.cfcPath);
        	data.setEL(creator.createKey("gatewayId"), getId());
        	data.setEL(creator.createKey("gatewayType"), "RedisSubPub");
        	
        Struct event=creator.createStruct();
	        event.setEL(creator.createKey("data"), data);
	        event.setEL(creator.createKey("channel"), channel);
	        
        if (engine.invokeListener(this, "onIncomingMessage", event)) {
        	engine.log(this, GatewayEngine.LOGLEVEL_INFO, "RedisSubGateway(" + getId() + ") was invoked:");
        } else {
        	engine.log(this, GatewayEngine.LOGLEVEL_ERROR, "RedisSubGateway(" + getId() + ") Failed to invoke");
        }
		
	}

}
