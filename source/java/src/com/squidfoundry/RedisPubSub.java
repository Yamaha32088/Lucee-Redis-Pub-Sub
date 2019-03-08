package com.squidfoundry;

import java.util.Map;

import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.io.IOException;

import lucee.runtime.gateway.Gateway;
import lucee.runtime.gateway.GatewayEngine;
import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.runtime.type.Struct;
import lucee.runtime.util.Creation;

public class RedisPubSub extends JedisPubSub implements Gateway {
	
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
	private int restarts = 0;

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
		
		info("initializing");
		info("RedisSubGateway(" + getId() + ") configured for " + host
				+ ":" + port + "::" + channel + ".");
	}

	public String sendMessage(Map<?, ?> _data) {
		String status="OK";
		info("Message from gateway was:" + _data.get("message").toString());

		try(Jedis redisClient = pool.getResource()) {
			redisClient.publish(channel, _data.get("message").toString());
		} catch(JedisConnectionException e) {
			doRestart();
		}
		
		return status;
	}

	public void doStart() {
		state = STARTING;
		try {
			info("started");
			new Thread(new Runnable() {
				public void run() {
					startJedis();
				}
			}).start();
			
			state = RUNNING;
			info("running");
		} catch(Exception e) {
			state = FAILED;
			error(e.getMessage());
		}
		
	}

	public void doRestart() {
		doStop();
		pool = getPool();
		doStart();
	}

	public void doStop() {
		state = STOPPING;
		try {
			pool.destroy();
		} catch(Exception e) {
			e.printStackTrace();
			state = FAILED;
		}
		state = STOPPED;
	}
	
	@Override
	public void onMessage(String channel, String message) {
		info("RedisSubGateway(" + getId()
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
        	info("RedisSubGateway(" + getId() + ") was invoked:");
        } else {
        	error("RedisSubGateway(" + getId() + ") Failed to invoke");
        }	
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
	
	private void startJedis() {
		if(pool == null) {
			pool = getPool();
		}
		
		try(Jedis redisClient = pool.getResource()) {
			redisClient.subscribe(this, channel);
		} catch(Exception e) {
			e.printStackTrace();
			state = FAILED;
			if(restarts <= 3) {
				restarts++;
				doRestart();
			}
		}
	}
	
	protected JedisPool getPool() {
		try {
			pool = new JedisPool(getJedisPoolConfig(), host, port, 2000);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return pool;
	}

	protected JedisPoolConfig getJedisPoolConfig() throws IOException {
		JedisPoolConfig config = new JedisPoolConfig();
		return config;
	}
	
	private void info(String msg) {
		engine.log(this, GatewayEngine.LOGLEVEL_INFO, msg);
	}
	
	private void error(String msg) {
		engine.log(this, GatewayEngine.LOGLEVEL_ERROR, msg);
	}

}