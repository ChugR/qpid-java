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

define(["dojo/_base/xhr",
        "dojo/_base/event",
        "dojo/_base/json",
        "dojo/_base/lang",
        "dojo/dom-construct",
        "dojo/dom-geometry",
        "dojo/window",
        "dijit/TitlePane",
        "dijit/Dialog",
        "dijit/form/Form",
        "dijit/form/Button",
        "dijit/form/RadioButton",
        "dijit/form/CheckBox",
        "dojox/layout/TableContainer",
        "dojox/layout/ScrollPane",
        "dojox/validate/us",
        "dojox/validate/web",
        "dojo/domReady!"
        ],
       function (xhr, event, json, lang, dom, geometry, win) {
           var util = {};
           if (Array.isArray) {
               util.isArray = function (object) {
                   return Array.isArray(object);
               };
           } else {
               util.isArray = function (object) {
                   return object instanceof Array;
               };
           }

           util.flattenStatistics = function (data) {
               var attrName, stats, propName, theList;
               for(attrName in data) {
                   if(data.hasOwnProperty(attrName)) {
                       if(attrName == "statistics") {
                           stats = data.statistics;
                           for(propName in stats) {
                               if(stats.hasOwnProperty( propName )) {
                                   data[ propName ] = stats[ propName ];
                               }
                           }
                       } else if(data[ attrName ] instanceof Array) {
                           theList = data[ attrName ];

                           for(var i=0; i < theList.length; i++) {
                               util.flattenStatistics( theList[i] );
                           }
                       }
                   }
               }
           };

           util.isReservedExchangeName = function(exchangeName)
           {
               return exchangeName == null || exchangeName == "" || "<<default>>" == exchangeName || exchangeName.indexOf("amq.") == 0 || exchangeName.indexOf("qpid.") == 0;
           };

           util.deleteGridSelections = function(updater, grid, url, confirmationMessageStart)
           {
               var data = grid.selection.getSelected();

               if(data.length)
               {
                   var confirmationMessage = null;
                   if (data.length == 1)
                   {
                       confirmationMessage = confirmationMessageStart + " '" + data[0].name + "'?";
                   }
                   else
                   {
                       var names = '';
                       for(var i = 0; i<data.length; i++)
                       {
                           if (names)
                           {
                               names += ', ';
                           }
                           names += "\""+ data[i].name + "\"";
                       }
                       confirmationMessage = confirmationMessageStart + "s " + names + "?";
                   }
                   if(confirm(confirmationMessage))
                   {
                       var i, queryParam;
                       for(i = 0; i<data.length; i++)
                       {
                           if(queryParam)
                           {
                               queryParam += "&";
                           }
                           else
                           {
                               queryParam = "?";
                           }
                           queryParam += "id=" + data[i].id;
                       }
                       var query = url + queryParam;
                       var success = true
                       var failureReason = "";
                       xhr.del({url: query, sync: true, handleAs: "json"}).then(
                           function(data)
                           {
                               // TODO why query *??
                               //grid.setQuery({id: "*"});
                               grid.selection.deselectAll();
                               updater.update();
                           },
                           function(error) {success = false; failureReason = error;});
                       if(!success )
                       {
                           alert("Error:" + failureReason);
                       }
                   }
               }
           }

           util.isProviderManagingUsers = function(type)
           {
               return (type === "PlainPasswordFile" || type === "Base64MD5PasswordFile");
           };

           util.showSetAttributesDialog = function(attributeWidgetFactories, data, putURL, dialogTitle)
           {
              var layout = new dojox.layout.TableContainer({
                   cols: 1,
                   "labelWidth": "300",
                   showLabels: true,
                   orientation: "horiz",
                   customClass: "formLabel"
              });
              var submitButton = new dijit.form.Button({label: "Submit", type: "submit"});
              var form = new dijit.form.Form();

              var dialogContent = dom.create("div");
              var dialogContentArea = dom.create("div", { "class": "dijitDialogPaneContentArea"});
              var dialogActionBar = dom.create("div", { "class": "dijitDialogPaneActionBar"} );
              dialogContent.appendChild(dialogContentArea);
              dialogContent.appendChild(dialogActionBar);
              dialogContentArea.appendChild(layout.domNode)
              dialogActionBar.appendChild(submitButton.domNode);
              form.domNode.appendChild(dialogContent);

              var widgets = {};
              var requiredFor ={};
              var groups = {};
              for(var i in attributeWidgetFactories)
              {
                 var attributeWidgetFactory = attributeWidgetFactories[i];
                 var widget = attributeWidgetFactory.createWidget(data);
                 var name = attributeWidgetFactory.name ? attributeWidgetFactory.name : widget.name;
                 widgets[name] = widget;
                 widget.initialValue = widget.value;
                 var dotPos = name.indexOf(".");
                 if (dotPos == -1)
                 {
                   layout.addChild(widget);
                 }
                 else
                 {
                   var groupName = name.substring(0, dotPos);
                   var groupFieldContainer = null;
                   if (groups.hasOwnProperty(groupName))
                   {
                       groupFieldContainer = groups[groupName];
                   }
                   else
                   {
                     groupFieldContainer = new dojox.layout.TableContainer({
                           cols: 1,
                           "labelWidth": "290",
                           showLabels: true,
                           orientation: "horiz",
                           customClass: "formLabel"
                      });
                     groups[groupName] = groupFieldContainer;
                     var groupTitle = attributeWidgetFactory.groupName ? attributeWidgetFactory.groupName :
                                         groupName.charAt(0).toUpperCase() + groupName.slice(1);
                     var panel = new dijit.TitlePane({title: groupTitle, toggleable: false, content: groupFieldContainer.domNode});
                     dialogContentArea.appendChild(dom.create("br"));
                     dialogContentArea.appendChild(panel.domNode);
                   }
                   groupFieldContainer.addChild(widget);
                 }
                 if (attributeWidgetFactory.hasOwnProperty("requiredFor") && !data[name])
                 {
                   requiredFor[attributeWidgetFactory.requiredFor] = widget;
                 }
              }

              // add onchange handler to set required property for dependent widget
              for(var widgetName in requiredFor)
              {
                var dependent = requiredFor[widgetName];
                var widget = widgets[widgetName];
                if (widget)
                {
                  widget.dependent = dependent;
                  widget.on("change", function(newValue){
                    this.dependent.set("required", newValue != "");
                  });
                }
              }
              var setAttributesDialog = new dijit.Dialog({
                 title: dialogTitle,
                 content: form,
                 style: "width: 600px; max-height: 80%"
             });
             form.on("submit", function(e)
             {
               event.stop(e);
               try
               {
                 if(form.validate())
                 {
                   var values = {};
                   for(var i in widgets)
                   {
                       var widget = widgets[i];
                       var value = widget.value;
                       var propName = widget.name;
                       if ((widget instanceof dijit.form.CheckBox || widget instanceof dijit.form.RadioButton))
                       {
                         values[ propName ] = widget.checked;
                       }
                       else if (value != widget.initialValue)
                       {
                         values[ propName ] = value ? value: null;
                       }
                   }

                     var that = this;
                     xhr.put({url: putURL, sync: true, handleAs: "json",
                              headers: { "Content-Type": "application/json"},
                              putData: json.toJson(values),
                              load: function(x) {that.success = true; },
                              error: function(error) {that.success = false; that.failureReason = error;}});
                     if(this.success === true)
                     {
                         setAttributesDialog.destroy();
                     }
                     else
                     {
                         alert("Error:" + this.failureReason);
                     }
                     return false;
                 }
                 else
                 {
                     alert('Form contains invalid data.  Please correct first');
                     return false;
                 }
               }
               catch(e)
               {
                  alert("Unexpected exception:" + e.message);
                  return false;
               }
             });
             form.connectChildren(true);
             setAttributesDialog.startup();
             setAttributesDialog.on("show", function(){
               var data = geometry.position(dialogContentArea);
               var maxHeight = win.getBox().h * 0.6;
               if (data.h > maxHeight)
               {
                 dialogContentArea.style.height = maxHeight + "px";
                 dialogContentArea.style.overflow= "auto";
               }
             })
             setAttributesDialog.show();
           };

           return util;
       });