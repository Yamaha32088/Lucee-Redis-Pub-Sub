package org.squidfoundry.redisgateway;

import java.util.Map;

import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.Jedis;
import lucee.runtime.gateway.Gateway;
import lucee.runtime.gateway.GatewayEngine;
import lucee.loader.engine.CFMLEngine;
import lucee.loader.engine.CFMLEngineFactory;
import lucee.runtime.type.Struct;
import lucee.runtime.util.Creation;

public class RedisSubGateway extends JedisPubSub implements Gateway {
	
	protected Jedis jedisSub;
	protected Jedis jedisPub;
	protected Thread clientThread;
	
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

	public void init(GatewayEngine engine, String id, String cfcPath, Map config) {
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
	
	public void doRestart() {
		doStop();
		doStart();
		
	}

	public void doStart() {
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
		jedisPub = new Jedis(host, port);
		jedisPub.connect();
		
		jedisSub = new Jedis(host, port);
		jedisSub.connect();
//		if (auth != null)
//			jedis.auth("foobared");
		jedisSub.flushAll();
		jedisSub.subscribe(this, channel);

	}

	public void doStop() {
		state = STOPPING;
		engine.log(this,GatewayEngine.LOGLEVEL_INFO,"stopping");
		this.unsubscribe();
		jedisSub.disconnect();
		state = STOPPED;
		
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

	public String sendMessage(Map _data) {
		String status="OK";
		engine.log(this, GatewayEngine.LOGLEVEL_INFO, "Message from gateway was:" + _data.get("message").toString());
		jedisPub.publish(channel, _data.get("message").toString());
		return status;
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
