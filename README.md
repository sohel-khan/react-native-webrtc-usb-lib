# react-native-webrtc-usb-lib

<!-- forked from https://github.com/oney/react-native-webrtc
usb just for android. -->

Forked from https://github.com/jatecl/react-native-webrtc-usb and upgraded package versions, solved few Bugs and published my own npm.
Credit goes to [#jatecl](https://www.npmjs.com/~jatecl)
 
[![npm version](https://badge.fury.io/js/react-native-webrtc.svg)](https://badge.fury.io/js/react-native-webrtc)
[![npm downloads](https://img.shields.io/npm/dm/react-native-webrtc.svg?maxAge=2592000)](https://img.shields.io/npm/dm/react-native-webrtc.svg?maxAge=2592000)

A WebRTC module for React Native.
<!-- 
# BREAKING FOR RN 40:

`master` branch needs RN >= 0.62.0 for now.
if your RN version is under 40, use branch [rn-less-40](https://github.com/oney/react-native-webrtc/tree/rn-less-40) (npm version `0.54.7`)

see [#190](https://github.com/oney/react-native-webrtc/pull/190) for detials -->

## Support
- Currently support for iOS and Android.  
- Support video and audio communication.  
- Supports data channels.  
- You can use it to build an iOS/Android app that can communicate with web browser.  

## Installation

<!-- ### react-native-webrtc:

<!-- - [iOS](https://github.com/oney/react-native-webrtc/blob/master/Documentation/iOSInstallation.md) -->
- [Android](https://github.com/react-native-webrtc/react-native-webrtc/blob/master/Documentation/AndroidInstallation.md) -->

<!-- note: 0.10.0~0.12.0 required `git-lfs`, see: [git-lfs-installation](https://github.com/oney/react-native-webrtc/blob/master/Documentation/git-lfs-installation.md) -->

### Android installation:
npm install react-native-webrtc-usb-lib  --save


Starting with React Native 0.60 auto-linking works out of the box, so there are no extra steps.

### Declaring permissions
```javascript

 <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.BLUETOOTH" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
```

### Add this line to android/gradle.properties:
If you are getting this error:

```javascript
Fatal Exception: java.lang.UnsatisfiedLinkError: No implementation found for void org.webrtc.PeerConnectionFactory.nativeInitializeAndroidGlobals() (tried Java_org_webrtc_PeerConnectionFactory_nativeInitializeAndroidGlobals and Java_org_webrtc_PeerConnectionFactory_nativeInitializeAndroidGlobals__)
       at org.webrtc.PeerConnectionFactory.nativeInitializeAndroidGlobals(PeerConnectionFactory.java)
       at org.webrtc.PeerConnectionFactory.initialize(PeerConnectionFactory.java:306)
       at com.oney.WebRTCModule.WebRTCModule.initAsync(WebRTCModule.java:79)
       at com.oney.WebRTCModule.WebRTCModule.lambda$new$0(WebRTCModule.java:70)
       at com.oney.WebRTCModule.-$$Lambda$WebRTCModule$CnyHZvkjDxq52UReGHUZlY0JsVw.run(-.java:4)
       at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1162)
       at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:636)
       at java.lang.Thread.run(Thread.java:764)
```

Add this line to android/gradle.properties:
```javascript
# This one fixes a weird WebRTC runtime problem on some devices.
# https://github.com/jitsi/jitsi-meet/issues/7911#issuecomment-714323255
android.enableDexingArtifactTransform.desugaring=false

```


## Usage
Now, you can use WebRTC like in browser.
In your `index.ios.js`/`index.android.js`, you can require WebRTC to import RTCPeerConnection, RTCSessionDescription, etc.
Anything about using RTCPeerConnection, RTCSessionDescription and RTCIceCandidate is like browser.  
Support most WebRTC APIs, please see the [Document](https://developer.mozilla.org/en-US/docs/Web/API/RTCPeerConnection).
```javascript

import React from 'react';
import {
  SafeAreaView,
  StyleSheet,
  ScrollView,
  View,
  Text,
  Alert,
  TouchableOpacity,
  Dimensions,
  BackHandler
} from 'react-native';

import {
  RTCPeerConnection,
  RTCIceCandidate,
  RTCSessionDescription,
  RTCView,
  MediaStream,
  MediaStreamTrack,
	getUserMedia,
	
} from "react-native-webrtc-usb-lib";

import io from 'socket.io-client'
import { SOCKET_IO_SERVER} from "./config"

const dimensions = Dimensions.get('window')

class App extends React.Component {
  constructor(props) {
    super(props)

    this.sdp
    this.socket = null
    this.candidates = [];

    this.serviceIP = SOCKET_IO_SERVER

    this.state = {
      localStream: null,
      remoteStream: null,
      disconnected: false,
      isCalling: false,

      pc_config: {
        "iceServers": [
          {
            urls : 'stun:stun.l.google.com:19302'
          }
        ]
      },

      sdpConstraints: {
        'mandatory': {
            'OfferToReceiveAudio': true,
            'OfferToReceiveVideo': true
        }
      },
    }
  }


  componentDidMount = () => {

		const { roomId} = "test";
    
    this.socket = io.connect(
      this.serviceIP,
      {
        path: '/io/webrtc',
        query: {
          room: `/${roomId.toLocaleLowerCase().trim()}`
        }
      }
    )

    this.socket.on('connection-success', success => {
      console.log("connection-success ::", success)
    })

    this.socket.on('offerOrAnswer', (sdp) => {

      this.sdp = JSON.stringify(sdp)

      // set sdp as remote description
      this.pc.setRemoteDescription(new RTCSessionDescription(sdp))
      this.setState({isCalling: true})

    })

    this.socket.on('candidate', (candidate) => {
      // console.log('From Peer... ', JSON.stringify(candidate))
      this.pc.addIceCandidate(new RTCIceCandidate(candidate))

    })


    this.pc = new RTCPeerConnection(this.state.pc_config)

    this.pc.onicecandidate = (e) => {
      // send the candidates to the remote peer
      // see addCandidate below to be triggered on the remote peer
      if (e.candidate) {
        // console.log(JSON.stringify(e.candidate))
        this.sendToPeer('candidate', e.candidate)
      }
    }

    // triggered when there is a change in connection state
    this.pc.oniceconnectionstatechange = (e) => {
      // console.log(e)
    }

    this.pc.onaddstream = (e) => {
      // debugger
      this.setState({
        remoteStream: e.stream
      })
    }

    this.socket.on('peer-disconnected', data => {
      console.log('In peer-disconnected', data)
      console.log('In peer-disconnected--', this.state.remoteStream);

      if(this.state.remoteStream){

        this.stopTracks(this.state.remoteStream);
        // this.pc.close()

        this.setState({
          disconnected: true,
          remoteStream: null,
          isCalling: false,
        })
        Alert.alert(
          'Call Ended',
          'Your friend has ended a call...',
          [
            {
              text: 'OK',
              onPress: () => {
              this.props.navigation.goBack();
                
              },
            },
          ],
          {cancelable: false},
        );

      }

    })
    
    const success = (stream) => {
      // console.log("In getUserMedia success ::", stream)
      this.setState({
        localStream: stream
      })
      this.pc.addStream(stream)
    }

    const failure = (e) => {
      console.log('getUserMedia Error: ', e)
    }

		let isFront = true;
		
		MediaStreamTrack
		.getSources()
		.then(async sourceInfos => {
			// console.log("devices list ::", sourceInfos);
			
			let videoSourceId, device;
			for (let i = 0; i < sourceInfos.length; i++) {
				const sourceInfo = sourceInfos[i];
				if(sourceInfo.kind == "video" || sourceInfo.facing == "usb") {

					videoSourceId = sourceInfo.id;
					device = sourceInfo.facing
				}
			}
			console.log("videoSourceId  ::", videoSourceId, device);

      const constraints = {
        audio: true,
        video: {
          mandatory: {
            minWidth: 1280, // Provide your own width, height and frame rate here
            minHeight: 720,
            minFrameRate: 30
          },
          facingMode: (isFront ? "user" : "environment"),
          optional: (videoSourceId ? [{ sourceId: videoSourceId }] : [])
        }
      }

      return getUserMedia(constraints)
        .then(success)
        .catch(failure);
    });
  }

    sendToPeer = (messageType, payload) => {
      // console.log('=================================')
      
      // console.log('sendToPeer ::', messageType, payload);
      // console.log('=================================')

      this.socket.emit(messageType, {
        socketID: this.socket.id,
        payload
      })
    }

    createOffer = () => {
      console.log('Offer')
  
      // https://developer.mozilla.org/en-US/docs/Web/API/RTCPeerConnection/createOffer
      // initiates the creation of SDP
      this.pc.createOffer(this.state.sdpConstraints)
        .then(sdp => {
          // console.log(JSON.stringify(sdp))
  
          // set offer sdp as local description
          this.pc.setLocalDescription(sdp)
          this.setState({isCalling: true})
  
          this.sendToPeer('offerOrAnswer', sdp)
      })
      .catch(err => console.log("In createOffer catch ::", err))

    }
    
    createAnswer = () => {
      console.log('Answer')
      this.pc.createAnswer(this.state.sdpConstraints)
        .then(sdp => {
          this.pc.setLocalDescription(sdp)
  
          this.sendToPeer('offerOrAnswer', sdp)
      })
      .catch(err => console.log("In answer catch ::", err))
    }

    setRemoteDescription = () => {
      // retrieve and parse the SDP copied from the remote peer
      const desc = JSON.parse(this.sdp)
  
      // set sdp as remote description
      this.pc.setRemoteDescription(new RTCSessionDescription(desc))
    }

    addCandidate = () => {
      this.candidates.forEach(candidate => {
        console.log(JSON.stringify(candidate))
        this.pc.addIceCandidate(new RTCIceCandidate(candidate))
      });
    }

    stopTracks = (stream) => {
      stream.getTracks().forEach(track => track.stop());
    }

    disconnect = ()=>{
      this.stopTracks(this.state.localStream);
      this.socket.close();
      this.pc.close()
      this.props.navigation.goBack();
    }

  render() {
    const {
      localStream,
      remoteStream,
    } = this.state

    const remoteVideo = remoteStream ?
      (
        <RTCView
              // key={1}
              // zOrder={0}
              objectFit='cover'
              style={{ ...styles.rtcView }}
              streamURL={remoteStream && remoteStream.toURL()}
              />
      ) :
      (
        <View style={{ padding: 15, }}>
          <Text style={{ fontSize:22, textAlign: 'center', color: 'white' }}>Waiting for Peer connection ...</Text>
        </View>
      )

    return (
      
      <SafeAreaView style={{ flex: 1, }}>
          <View style={{...styles.buttonsContainer}}>
            { this.state.remoteStream == null &&
            <View style={{ flex: 1, }}>
              <TouchableOpacity onPress={this.createOffer}>
                  <View style={styles.button}>
                  <Text style={{ ...styles.textContent, }}>{ this.state.isCalling ? "Calling..." :"Call"}</Text>
                </View>
              </TouchableOpacity>
            </View>
            }

            { this.state.isCalling && this.state.remoteStream != null && 
            <View style={{ flex: 1, }}>
              <TouchableOpacity onPress={this.createAnswer}>
                <View style={styles.button}>
                  <Text style={{ ...styles.textContent, }}>Answer</Text>
                </View>
              </TouchableOpacity>
            </View>
            }

          { this.state.remoteStream != null &&

            <View style={{ flex: 1, }}>
              <TouchableOpacity onPress={this.disconnect}>
                <View style={styles.button}>
                  <Text style={{ ...styles.textContent, color: 'red'}}>Disconnect</Text>
                </View>
              </TouchableOpacity>
            </View>
           }

          </View>
          <View style={{ ...styles.videosContainer, }}>
          
              <View style={{flex: 1 }}>
                  <View>
                  <RTCView
                    objectFit='cover'
                    style={{ ...styles.rtcView }}
                    streamURL={this.state.localStream && this.state.localStream.toURL()}
                    />
                  </View>
              </View>
          </View>

          <ScrollView style={{ ...styles.scrollView }}>
            <View style={{
              flex: 1,
              width: '100%',
              backgroundColor: 'black',
              justifyContent: 'center',
              alignItems: 'center',
            }}>
              { remoteVideo }
            </View>
          </ScrollView>
        </SafeAreaView>
      );
  }
};

const styles = StyleSheet.create({
  buttonsContainer: {
    flexDirection: 'row',
  },
  button: {
    margin: 5,
    paddingVertical: 10,
    backgroundColor: 'lightgrey',
    borderRadius: 5,
  },
  textContent: {
    fontFamily: 'Avenir',
    fontSize: 20,
    textAlign: 'center',
  },
  videosContainer: {
    flex: 1,
    flexDirection: 'row',
    justifyContent: 'center',
    marginLeft: 20
  },
  rtcView: {
    width: 300, //dimensions.width,
    height: 250,//dimensions.height / 2,
    backgroundColor: 'black',
    justifyContent: 'center',
    alignItems: 'center',
  },
  scrollView: {
    flex: 1,
    // flexDirection: 'row',
    backgroundColor: 'teal',
    padding: 15,
  },
  rtcViewRemote: {
    width: dimensions.width - 30,
    height: 300,//dimensions.height / 2,
    backgroundColor: 'black',
  }
});

export default App;

```

<!-- ## Demos

**Official Demo**

author: [@oney](https://github.com/oney)

The demo project is https://github.com/oney/RCTWebRTCDemo   
And you will need a signaling server. I have written a signaling server https://react-native-webrtc.herokuapp.com/ (the repository is https://github.com/oney/react-native-webrtc-server).   
You can open this website in browser, and then set it as signaling server in the app, and run the app. After you enter the same room ID, the video stream will be connected.

**Demo by Folks**

author: [@thoqbk](https://github.com/thoqbk)
- Signaling server and web app: https://rewebrtc.herokuapp.com/ (the repository is https://github.com/thoqbk/rewebrtc-server)
- React native app repository: https://github.com/thoqbk/rewebrtc -->
