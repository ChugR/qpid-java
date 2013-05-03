/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
define(["dojo/dom",
        "dojo/_base/xhr",
        "dojo/parser",
        "dojo/query",
        "dojo/_base/connect",
        "dijit/registry",
        "qpid/common/properties",
        "qpid/common/updater",
        "qpid/common/util",
        "qpid/common/formatter",
        "qpid/management/addPort",
        "dojo/domReady!"],
       function (dom, xhr, parser, query, connect, registry, properties, updater, util, formatter, addPort) {

           function Port(name, parent, controller) {
               this.name = name;
               this.controller = controller;
               this.modelObj = { type: "port", name: name };
               if(parent) {
                   this.modelObj.parent = {};
                   this.modelObj.parent[ parent.type] = parent;
               }
           }

           Port.prototype.getTitle = function() {
               return "Port: " + this.name;
           };

           Port.prototype.open = function(contentPane) {
               var that = this;
               this.contentPane = contentPane;
               xhr.get({url: "showPort.html",
                        sync: true,
                        load:  function(data) {
                            contentPane.containerNode.innerHTML = data;
                            parser.parse(contentPane.containerNode);

                            that.portUpdater = new PortUpdater(contentPane.containerNode, that.modelObj, that.controller, "rest/port/" + encodeURIComponent(that.name));

                            updater.add( that.portUpdater );

                            that.portUpdater.update();

                            var deletePortButton = query(".deletePortButton", contentPane.containerNode)[0];
                            var node = registry.byNode(deletePortButton);
                            connect.connect(node, "onClick",
                                function(evt){
                                    that.deletePort();
                                });

                            var editPortButton = query(".editPortButton", contentPane.containerNode)[0];
                            var node = registry.byNode(editPortButton);
                            connect.connect(node, "onClick",
                                function(evt){
                                  that.showEditDialog();
                                });
                        }});
           };

           Port.prototype.close = function() {
               updater.remove( this.portUpdater );
           };


           Port.prototype.deletePort = function() {
               if(confirm("Are you sure you want to delete port '" +this.name+"'?")) {
                   var query = "rest/port/" + encodeURIComponent(this.name);
                   this.success = true
                   var that = this;
                   xhr.del({url: query, sync: true, handleAs: "json"}).then(
                       function(data) {
                           that.contentPane.onClose()
                           that.controller.tabContainer.removeChild(that.contentPane);
                           that.contentPane.destroyRecursive();
                           that.close();
                       },
                       function(error) {that.success = false; that.failureReason = error;});
                   if(!this.success ) {
                       alert("Error:" + this.failureReason);
                   }
               }
           }

           Port.prototype.showEditDialog = function() {
               var that = this;
               xhr.get({url: "rest/broker", sync: properties.useSyncGet, handleAs: "json"})
               .then(function(data)
                     {
                         var brokerData= data[0];
                         addPort.show(that.name, brokerData.authenticationproviders, brokerData.keystores, brokerData.truststores);
                     }
               );
           }

           function PortUpdater(containerNode, portObj, controller, url)
           {
               var that = this;

               function findNode(name) {
                   return query("." + name, containerNode)[0];
               }

               function storeNodes(names)
               {
                  for(var i = 0; i < names.length; i++) {
                      that[names[i]] = findNode(names[i]);
                  }
               }

               storeNodes(["nameValue",
                           "stateValue",
                           "portValue",
                           "authenticationProviderValue",
                           "protocolsValue",
                           "transportsValue",
                           "bindingAddressValue",
                           "keyStoreValue",
                           "needClientAuthValue",
                           "wantClientAuthValue",
                           "trustStoresValue",
                           "bindingAddress",
                           "keyStore",
                           "needClientAuth",
                           "wantClientAuth",
                           "trustStores"
                           ]);

               this.query = url;

               xhr.get({url: this.query, sync: properties.useSyncGet, handleAs: "json"}).then(function(data)
                               {
                                  that.keyStoreData = data[0];
                                  that.updateHeader();
                               });

           }

           PortUpdater.prototype.updateHeader = function()
           {
               function printArray(fieldName, object)
               {
                   var array = object[fieldName];
                   var data = "<div>";
                   if (array) {
                       for(var i = 0; i < array.length; i++) {
                           data+= "<div>" + array[i] + "</div>";
                       }
                   }
                   return data + "</div>";
               }

              this.nameValue.innerHTML = this.keyStoreData[ "name" ];
              this.stateValue.innerHTML = this.keyStoreData[ "state" ];
              this.portValue.innerHTML = this.keyStoreData[ "port" ];
              this.authenticationProviderValue.innerHTML = this.keyStoreData[ "authenticationProvider" ] ? this.keyStoreData[ "authenticationProvider" ] : "";
              this.protocolsValue.innerHTML = printArray( "protocols", this.keyStoreData);
              this.transportsValue.innerHTML = printArray( "transports", this.keyStoreData);
              this.bindingAddressValue.innerHTML = this.keyStoreData[ "bindingAddress" ] ? this.keyStoreData[ "bindingAddress" ] : "" ;
              this.keyStoreValue.innerHTML = this.keyStoreData[ "keyStore" ] ? this.keyStoreData[ "keyStore" ] : "";
              this.needClientAuthValue.innerHTML = "<input type='checkbox' disabled='disabled' "+(this.keyStoreData[ "needClientAuth" ] ? "checked='checked'": "")+" />" ;
              this.wantClientAuthValue.innerHTML = "<input type='checkbox' disabled='disabled' "+(this.keyStoreData[ "wantClientAuth" ] ? "checked='checked'": "")+" />" ;
              this.trustStoresValue.innerHTML = printArray( "trustStores", this.keyStoreData);
              var amqpProtocol = this.keyStoreData["protocols"][0] && this.keyStoreData["protocols"][0].indexOf("AMQP") == 0;
              this.bindingAddress.style.display= amqpProtocol? "block" : "none";
              var sslTransport = this.keyStoreData["transports"][0] && this.keyStoreData["transports"][0] == "SSL";
              var displayStyle = sslTransport ? "block" : "none";
              this.trustStoresValue.style.display = displayStyle;
              this.keyStore.style.display = displayStyle;
              this.needClientAuth.style.display = displayStyle;
              this.wantClientAuth.style.display = displayStyle;
              this.trustStores.style.display = displayStyle;
           };

           PortUpdater.prototype.update = function()
           {

              var thisObj = this;

              xhr.get({url: this.query, sync: properties.useSyncGet, handleAs: "json"}).then(function(data)
                   {
                      thisObj.keyStoreData = data[0];
                      thisObj.updateHeader();
                   });
           };

           return Port;
       });
