component extends='Gateway' {

    variables.fields = array(
        field(
            'Host',
            'host',
            '127.0.0.1',
            true,
            'The host path Redis instance',
            'text'
        ), field(
            'Port',
            'port',
            '6379',
            true,
            'The port number Redis instance',
            'text'
        ), field(
            'Password',
            'auth',
            '',
            false,
            'Password for Redis instance (optional)',
            'text'
        ), field(
            'Channel',
            'channel',
            'foo.bar',
            true,
            'The channel to subscribe to',
            'text'
        )
    );

    string function getListenerCfcMode() {
    	return 'required';
    }

    string function getClass() {
        return 'com.squidfoundry.RedisPubSub';
    }

    string function getLabel() {
        return 'Lucee Redis Pub/Sub Gateway';
    }

    string function getDescription() {
        return 'This gateway handles Redis Pub/Sub events';
    }

}