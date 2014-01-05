// websocket connection with graceful fallack

var socket;

/**
* If the page came from a server we use a graceWebSocket which will fallback to AJAX. 
* If the page came from file://some/path/to/project/poker.html?room=1234&player=9887 to test html we connect to some harcoded server
*/
if( location.hostname.length > 0 ) {
    // connect the websocket to the same host and specified port passing the room number and player id
    socket = $.gracefulWebSocket("ws://" + location.hostname + ":" + port + "/websocket/" + room + "/" + player);
} else {
    // debugging a local html file point to any server e.g. point browser at file://some/path/to/project/poker.html?room=1234&player=9887
    // it is safe to edit this hardcoded connection when testing a skin in a local file
    socket = new WebSocket("ws://localhost:8080/websocket/"+ room + "/" + player);
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
            case 'ts':
            	// timestamp heartbeat
            	break;
            case 'close':
            	if( ! window.wsFallback ) socket.close();
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
        if( ! window.wsFallback ) {
        	// only close when we have a real websocket to close not when ajax polling
	        socket.onclose = function () {}; // disable onclose handler first
	        socket.close()
        }
    };        
};

/**
* If the websocketPort is not the same as the fallbackPort then the websocket port may be firewalled on the client side. 
* In which case the onclose will be called because a connection could never be established and we will fallback to ajax polling by reloading the page forcing the use of the fallback mechanism. 
* If the websocket port is the same as the fallback port (i.e. its the content port) then we must have had a connection to be running this script. In which case the server has been restarted so we try to reload the page to restart the game. 
* The worst case scenario is that everything was working, the websocketPort is not the same as the fallbackPort, and the server was restarted. In which case we fallback to polling which was not required for the next game. 
*/
socket.onclose = function(event) {
    var fallback = null;
    if( websocketPort != fallbackPort) {
    	fallback = true;
    } else {
    	fallback = false;
    }
    console.log("Web Socket closed reopening window with fallback:"+fallback);
    
    window.location.assign(window.location.pathname+'?room='+room+'&player='+player+'&fallback='+fallback);
};
