// websocket connection with graceful fallack

var socket;

/**
* If the page came from a server we use a graceWebSocket which will fallback to AJAX. 
* If the page came from file://some/path/to/project/poker.html?room=1234&player=9887 to test html we connect to some harcoded server
*/
if( location.hostname.length > 0 ) {
    // connect the websocket to the same host and specified port passing the room number and player id
    var wsurl = "ws://" + location.hostname + ":" + port + "/websocket/" + room + "/" + player;
    console.log(wsurl);
    socket = $.gracefulWebSocket(wsurl);
} else {
    // debugging a local html file point to any server e.g. point browser at file://some/path/to/project/poker.html?room=1234&player=9887
    // it is safe to edit this hardcoded connection when testing a skin in a local file
    socket = new WebSocket("ws://www.sprint-planning.info/websocket/"+ room + "/" + player);
}

/**
* The main message handler receiving json from the server 
*/
socket.onmessage = function(event) {
    var rawMsg = JSON.parse(event.data);
    var arrayMessage;
    if (rawMsg instanceof Array) {
    	arrayMessage = rawMsg;
    } else {
    	arrayMessage = [];
    	arrayMessage.push(rawMsg);
    }
    
    for (var i=0; i < arrayMessage.length; i++){
    	var msg = arrayMessage[i];
    	console.log("in>  "+JSON.stringify(msg));
    	switch(msg.mType){
            case 'RoomSize':
                sp_roomSize(msg.size);
                break;
            case 'DrawnSize':
                sp_drawnSize(msg.size);
                break;
            case 'CardSet':
                sp_recievedCardSet(msg.cards);
                break;
            case 'Reset': 
            	sp_reset();
            	break;
            case 'ts':
            	// timestamp heartbeat
            	break;
            case 'close':
		        socket.onclose = function () {}; // disable onclose handler first
		        socket.close()
            	break;
            default:
                console.log('unknown from server: '+event.data);
                break;
        }
    }
};

/**
* When the websocket is opened we send a handshake if required and register to logout when the window is closed. 
*/
socket.onopen = function(event) {
    console.log("Web Socket opened");
    window.websocketOpened = true;
    if( window.wsFallback ) {
    	// server doenst know about this player when faking websockets with ajax polling so we need to announce that we will be polling
    	console.log("sending handshake");
        socket.send("handshake");
    }
    
    window.onbeforeunload = function() {
    	// logout
        var msg = '{"player":'+player+',"mType":"PlayerExit"}';
        console.log("out> "+msg);
        sp_send(msg);
        socket.onclose = function () {}; // disable onclose handler first
        socket.close()
    };        
};

/**
* If the websocket was never seen to have opened then we assume it is firewalled or a proxy does not support websockets and we ask to fallback to ajax. 
*/
socket.onclose = function(event) {
    var fallback = false;
    if( ! window.websocketOpened ) {
    	console.error("websocket onclose with no onopen so falling back");
    	fallback = true;
    }
    console.log("Web Socket closed reopening window with fallback:"+fallback);
    window.location.assign(window.location.pathname+'?room='+room+'&player='+player+'&fallback='+fallback);
};
