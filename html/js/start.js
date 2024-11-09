'use strict';

//const vConsole = new VConsole();
//const remoteConsole = new RemoteConsole("http://[remote server]/logio-post");
//window.datgui = new dat.GUI();

const UUID_ANDROID_SERVICE = 'a9d158bb-9007-4fe3-b5d2-d3696a3eb067';
const UUID_ANDROID_WRITE = '52dc2801-7e98-4fc2-908a-66161b5959b0';
const UUID_ANDROID_READ = '52dc2802-7e98-4fc2-908a-66161b5959b0';
const UUID_ANDROID_NOTIFY = '52dc2803-7e98-4fc2-908a-66161b5959b0';

const ANDROID_WAIT = 200;

var bluetoothDevice = null;
var characteristics = new Map();

var vue_options = {
    el: "#top",
    mixins: [mixins_bootstrap],
    store: vue_store,
    router: vue_router,
    data: {
        ble_connected: false,
        ble_devicename: '', 
        ble_write_value: '',
        ble_read_value: '',
        ble_notify_value: '',
    },
    computed: {
    },
    methods: {
        ble_connect: function(){
            return this.requestDevice(UUID_ANDROID_SERVICE)
            .then( (name) => {
                this.progress_open();
                this.ble_devicename = name;
                return bluetoothDevice.gatt.connect()
                .then(server => {
                    console.log('Execute : getPrimaryService');
                    return wait_async(ANDROID_WAIT)
                    .then(() =>{
                        return server.getPrimaryService(UUID_ANDROID_SERVICE);
                    });
                })
                .then(service => {
                    console.log('Execute : getCharacteristic');
                    characteristics.clear();
                    return Promise.all([
                        this.setCharacteristic(service, UUID_ANDROID_WRITE),
                        this.setCharacteristic(service, UUID_ANDROID_READ),
                        this.setCharacteristic(service, UUID_ANDROID_NOTIFY)
                    ]);
                })
                .then(values => {
                    return wait_async(ANDROID_WAIT)
                    .then(() =>{
                        return this.startNotify(UUID_ANDROID_NOTIFY);
                    });
                })
                .then(() =>{
                    this.ble_connected = true;
                    console.log('ble_connect done');
                    return bluetoothDevice.name;
                })
                .catch(error =>{
                    alert(error);
                })
                .finally(() => {
                    this.progress_close();
                });
            })
            .catch(error =>{
                alert(error);
            });
        },
        ble_read: function(){
            return this.readChar(UUID_ANDROID_READ);
        },
        ble_write: function(){
            return this.writeChar(UUID_ANDROID_WRITE, this.hex2ba(this.ble_write_value, ''));
        },
        requestDevice: function(service_uuid){
            console.log('Execute : requestDevice');
    
            return navigator.bluetooth.requestDevice({
                filters: [{services:[ service_uuid ]}]
        //		acceptAllDevices: true,
        //		optionalServices: [service_uuid]
                }
            )
            .then(device => {
                console.log("requestDevice OK");
                characteristics.clear();
                bluetoothDevice = device;
                bluetoothDevice.addEventListener('gattserverdisconnected', this.onDisconnect);
                return bluetoothDevice.name;
            });
        },
        setCharacteristic: function(service, characteristicUuid) {
            console.log('Execute : setCharacteristic : ' + characteristicUuid);

            return wait_async(ANDROID_WAIT)
            .then(() => {
                return service.getCharacteristic(characteristicUuid);
            })
            .then( (characteristic) =>{
                characteristics.set(characteristicUuid, characteristic);
                characteristic.addEventListener('characteristicvaluechanged', this.onDataChanged);
                return service;
            });
        },
        onDisconnect: function(event){
            console.log('onDisconnect');
            characteristics.clear();
            this.ble_connected = false;
        },
        onDataChanged: function(event){
            console.log('onDataChanged');

            let characteristic = event.target;
            let packet = uint8array_to_array(characteristic.value);
            if( characteristic.uuid == UUID_ANDROID_READ ){
                this.ble_read_value = this.ba2hex(packet, '');
            }else if( characteristic.uuid == UUID_ANDROID_NOTIFY ){
                this.ble_notify_value = this.ba2hex(packet, '');
            }
        },    
        startNotify: function(uuid) {
            if( characteristics.get(uuid) === undefined )
                throw "Not Connected";
    
            console.log('Execute : startNotifications');
            return characteristics.get(uuid).startNotifications();
        },
        stopNotify: function(uuid){
            if( characteristics.get(uuid) === undefined )
                throw "Not Connected";
    
            console.log('Execute : stopNotifications');
            return characteristics.get(uuid).stopNotifications();
        },
        writeChar: function(uuid, array_value) {
            if( characteristics.get(uuid) === undefined )
                throw "Not Connected";
    
            console.log('Execute : writeValue');
            let data = Uint8Array.from(array_value);
            return characteristics.get(uuid).writeValue(data);
        },
        readChar: function(uuid){
            if( characteristics.get(uuid) === undefined )
                throw "Not Connected";
    
            console.log('Execute : readValue');
            return characteristics.get(uuid).readValue((dataView) =>{
                console.log(dataView);
            });
        }
    },
    created: function(){
    },
    mounted: function(){
        proc_load();
    }
};
vue_add_data(vue_options, { progress_title: '' }); // for progress-dialog
vue_add_global_components(components_bootstrap);
vue_add_global_components(components_utils);

/* add additional components */
  
window.vue = new Vue( vue_options );

function uint8array_to_array(array)
{
	var result = new Array(array.byteLength);
	var i;
	for( i = 0 ; i < array.byteLength ; i++ )
		result[i] = array.getUint8(i);
		
	return result;
}

function wait_async(timeout){
	return new Promise((resolve, reject) =>{
		setTimeout(resolve, timeout);
	});
}