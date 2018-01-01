package org.squidfoundry.redisgateway;

import java.io.IOException;
import java.util.Map;

import lucee.runtime.gateway.Gateway;
import lucee.runtime.gateway.GatewayEngine;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.Jedis;

import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.runtime.type.Struct;
import lucee.runtime.util.Cast;
import lucee.runtime.util.Creation;

public class RedisSubGateway extends JedisPubSub implements Gateway {
	
	protected Jedis jedis;
	protected Thread clientThread;
	
	private CFMLEngine cfmlEngine;
	private Cast caster;
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

	@Override
	public void init(GatewayEngine engine, String id, String cfcPath, Map config) {
		this.engine=engine;
		this.cfcPath=cfcPath;
		this.id=id;
		
		cfmlEngine=CFMLEngineFactory.getInstance();
		caster=cfmlEngine.getCastUtil();
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
	
	@Override
	public void doRestart() throws IOException {
		doStop();
		doStart();
		
	}

	@Override
	public void doStart() throws IOException {
		state = STARTING;
		engine.log(this,GatewayEngine.LOGLEVEL_INFO,"started");

		clientThread = new Thread(new Runnable() {
			public void run() {
				startJedis();
			}
		});
		
		clientThread.start();
		
		state = RUNNING;
		engine.log(this,GatewayEngine.LOGLEVEL_INFO,"running");
		
	}
	
	protected void startJedis() {
		engine.log(this, GatewayEngine.LOGLEVEL_INFO, host);
		jedis = new Jedis(host, port);
		jedis.connect();
//		if (auth != null)
//			jedis.auth("foobared");
		jedis.configSet("timeout", "300");
		jedis.flushAll();
		jedis.subscribe(this, channel);

	}

	@Override
	public void doStop() throws IOException {
		state = STOPPING;
		engine.log(this,GatewayEngine.LOGLEVEL_INFO,"stopping");
		this.unsubscribe();
		jedis.disconnect();
		state = STOPPED;
		
	}

	@Override
	public Object getHelper() {
		return null;
	}

	@Override
	public String getId() {
		return id;
	}
	
	 @Override
	public int getState() {
		return state;
	 }

	@Override
	public String sendMessage(Map _data) {
		String status="OK";
		engine.log(this,GatewayEngine.LOGLEVEL_INFO,_data.toString());
		return status;
	}

	@Override
	public void onMessage(String channel, String message) {
		engine.log(this, GatewayEngine.LOGLEVEL_INFO, "RedisSubGateway(" + getId()
				+ ") Message received. Message was: '"
				+ message.substring(0, Math.min(20, message.length())) + "'");
		
		Struct data=creator.createStruct();
        	data.setEL(creator.createKey("message"), message);
        	   
        Struct event=creator.createStruct();
	        event.setEL(creator.createKey("data"), data);
	        event.setEL(creator.createKey("originatorID"), "RedisSubGateway");
	        
	        event.setEL(creator.createKey("cfcMethod"), "onIncomingMessage");
	        event.setEL(creator.createKey("cfcTimeout"), new Double(10));
	        event.setEL(creator.createKey("cfcPath"), this.cfcPath);
	
	        event.setEL(creator.createKey("gatewayType"), "RedisSub");
	        event.setEL(creator.createKey("gatewayId"), getId());
        
        if (engine.invokeListener(this, "onIncomingMessage", event))
        	engine.log(this, GatewayEngine.LOGLEVEL_INFO, "RedisSubGateway(" + getId() + ") was invoked:");
        else
        	engine.log(this, GatewayEngine.LOGLEVEL_ERROR, "RedisSubGateway(" + getId() + ") Failed to invoke");
		
	}

	@Override
	public void onPMessage(String pattern, String channel, String message) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onPSubscribe(String pattern, int subscribedChannels) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onPUnsubscribe(String pattern, int subscribedChannels) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onSubscribe(String channel, int subscribedChannels) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onUnsubscribe(String channel, int subscribedChannels) {
		// TODO Auto-generated method stub

	}

}
