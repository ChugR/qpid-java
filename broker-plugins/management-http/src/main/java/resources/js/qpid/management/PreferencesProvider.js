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
define(["dojo/parser",
        "dojo/query",
        "dojo/dom-construct",
        "dojo/_base/array",
        "qpid/common/properties",
        "qpid/common/updater",
        "qpid/common/util",
        "dojo/_base/event",
        "dijit/registry",
        "dojo/dom-style",
        "dojox/html/entities",
        "qpid/management/addPreferencesProvider",
        "dojo/text!showPreferencesProvider.html",
        "dojo/domReady!"],
    function (parser,
              query,
              construct,
              array,
              properties,
              updater,
              util,
              event,
              registry,
              domStyle,
              entities,
              addPreferencesProvider,
              template)
    {

        function PreferencesProvider(name, parent, controller)
        {
            this.name = name;
            this.controller = controller;
            this.management = management;
            this.modelObj = {
                type: "preferencesprovider",
                name: name,
                parent: parent
            };
            this.authenticationProviderName = parent.name;
        }

        PreferencesProvider.prototype.getTitle = function ()
        {
            return "PreferencesProvider:" + this.authenticationProviderName + "/" + this.name;
        };

        PreferencesProvider.prototype.init = function (node, parentObject)
        {
            var that = this;
            node.innerHTML = template;
            parser.parse(node)
                .then(function (instances)
                {
                    that.containerNode = node;
                    that.parentObject = parentObject;
                    that.preferencesProviderType = query(".preferencesProviderType", node)[0];
                    that.preferencesProviderState = query(".preferencesProviderState", node)[0];
                    that.editPreferencesProviderButton = query(".editPreferencesProviderButton", node)[0];
                    that.deletePreferencesProviderButton = query(".deletePreferencesProviderButton", node)[0];
                    that.preferencesProviderAttributes = query(".preferencesProviderAttributes", node)[0];
                    that.preferencesDetailsDiv = query(".preferencesDetails", node)[0];
                    var editPreferencesProviderWidget = registry.byNode(that.editPreferencesProviderButton);
                    editPreferencesProviderWidget.on("click", function (evt)
                    {
                        event.stop(evt);
                        that.editPreferencesProvider();
                    });
                    var deletePreferencesProviderWidget = registry.byNode(that.deletePreferencesProviderButton);
                    deletePreferencesProviderWidget.on("click", function (evt)
                    {
                        event.stop(evt);
                        that.deletePreferencesProvider();
                    });
                });
        };

        PreferencesProvider.prototype.open = function (contentPane)
        {
            this.contentPane = contentPane;
            this.init(contentPane.containerNode);
            this.reload();
            this.updater = new PreferencesProviderUpdater(this);
            updater.add(this.updater);
        };

        PreferencesProvider.prototype.close = function ()
        {
            if (this.updater)
            {
                updater.remove(this.updater);
            }
        };

        PreferencesProvider.prototype.deletePreferencesProvider = function ()
        {
            if (this.preferencesProviderData)
            {
                var preferencesProviderData = this.preferencesProviderData;
                if (confirm("Are you sure you want to delete preferences provider '" + preferencesProviderData.name
                            + "'?"))
                {
                    var that = this;
                    this.management.remove(this.modelObj)
                        .then(function (data)
                        {
                            that.update(null);

                            // if opened in tab
                            if (that.contentPane)
                            {
                                that.close();
                                that.contentPane.onClose()
                                that.controller.tabContainer.removeChild(that.contentPane);
                                that.contentPane.destroyRecursive();
                            }
                            else
                            {
                                var widgets = registry.findWidgets(that.containerNode);
                                array.forEach(widgets, function (item)
                                {
                                    item.destroyRecursive();
                                });
                                construct.empty(that.containerNode);
                                if (that.parentObject)
                                {
                                    that.parentObject.onPreferencesProviderDeleted();
                                }
                            }
                        }, util.xhrErrorHandler);
                }
            }
        };

        PreferencesProvider.prototype.editPreferencesProvider = function ()
        {
            if (this.preferencesProviderData)
            {
                addPreferencesProvider.show(this.management, this.modelObj);
            }
        };

        PreferencesProvider.prototype.update = function (data)
        {
            this.preferencesProviderData = data;
            if (data)
            {
                this.name = data.name;
                this.preferencesProviderAttributes.style.display = 'block';
                this.editPreferencesProviderButton.style.display = 'inline';
                this.deletePreferencesProviderButton.style.display = 'inline';
                this.preferencesProviderType.innerHTML = entities.encode(String(data.type));
                this.preferencesProviderState.innerHTML = entities.encode(String(data.state));
                if (!this.details)
                {
                    var that = this;
                    require(["qpid/management/preferencesprovider/" + data.type.toLowerCase() + "/show"],
                        function (PreferencesProviderDetails)
                        {
                            that.details = new PreferencesProviderDetails(that.preferencesDetailsDiv);
                            that.details.update(data);
                        });
                }
                else
                {
                    this.details.update(data);
                }
            }
            else
            {
                this.editPreferencesProviderButton.style.display = 'none';
                this.deletePreferencesProviderButton.style.display = 'none';
                this.preferencesProviderAttributes.style.display = 'none';
                this.details = null;
            }
        };

        PreferencesProvider.prototype.reload = function ()
        {
            var that = this;
            that.management.load(that.modelObj, {excludeInheritedContext: true})
                .then(function (data)
                {
                    var preferencesProviderData = data[0];
                    util.flattenStatistics(preferencesProviderData);
                    that.update(preferencesProviderData);
                }, function (error)
                {
                    util.tabErrorHandler(error, {
                        updater: that.updater,
                        contentPane: that.contentPane,
                        tabContainer: that.controller.tabContainer,
                        name: that.modelObj.name,
                        category: "Preferences Provider"
                    });
                });
        };

        function PreferencesProviderUpdater(preferencesProvider)
        {
            this.preferencesProvider = preferencesProvider;
            this.contentPane = preferencesProvider.contentPane;
        };

        PreferencesProviderUpdater.prototype.update = function ()
        {
            if (!this.contentPane.selected)
            {
                return;
            }
            this.preferencesProvider.reload();
        };

        return PreferencesProvider;
    });
